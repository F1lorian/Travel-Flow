import {Component, inject, OnInit} from '@angular/core';
import {
 MatDialog, MatDialogRef,
  MAT_DIALOG_DATA,
  MatDialogTitle,
  MatDialogContent,
  MatDialogActions
} from '@angular/material/dialog';
import {FormsModule} from '@angular/forms';
import {Trip, TripService} from '../../TripManagement/services/trip.service';
import {DatePipe, SlicePipe} from '@angular/common';
import {MatIcon} from '@angular/material/icon';
import {EventService} from '../../../event-adding-manual/service/event.service';
import {ConflictDialog} from '../conflict-dialog/conflict-dialog';
import {Friend, FriendsService} from '../../services/friends.service';
import { UserEventDTO } from '../../../event-adding-manual/models/UserEventDTO';
import {forkJoin} from 'rxjs';
import {NotificationService} from '../notification-menu/notification.service';
import {FeedbackService} from '../../services/feedback.service';

interface EventDialogData {
  mode?: 'create' | 'edit' | 'show';
  id?: string;
  title?: string;
  kosten?: number;
  tripId?: number;
  start?: string;
  end?: string;
  creatorUsername?: string;
}

@Component({
  selector: 'app-event-dialog',
  standalone: true,
  imports: [
    FormsModule,
    DatePipe,
    MatIcon,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    SlicePipe
  ],
  templateUrl: './event-dialog.html',
  styleUrl: './event-dialog.css',
})
export class EventDialog implements OnInit {
  private readonly dialogRef = inject(MatDialogRef<EventDialog>);
  readonly data = inject<EventDialogData>(MAT_DIALOG_DATA);
  title = "";
  kosten: number | undefined;
  tripId: number | undefined;
  tripName = '';
  start = "";
  end = "";
  tripService = inject(TripService);
  eventService = inject(EventService);
  friendsService = inject(FriendsService);
  private readonly notificationService = inject(NotificationService);
  private readonly feedback = inject(FeedbackService);
  dialog = inject(MatDialog);
  trips: Trip[] = [];
  friends: Friend[] = [];
  allDay = false;
  errorMessage = "";
  inviteDropdownOpen = false;
  invitations: Friend[] = [];
  participants: UserEventDTO[] = [];

  mode: 'create' | 'edit' | 'show'= 'create';

  constructor() {
    if(this.data) {
      this.mode = this.data.mode || 'create';
      this.title = this.data.title || '';
      this.kosten = this.data.kosten || undefined;
      this.tripId = this.data.tripId || undefined;
      this.start = this.data.start || "";
      this.end = this.data.end || "";
    }
    console.log("data dialog");
    console.log(this.data);
  }

  ngOnInit() {
    this.loadTrips();
    this.loadFriends();

    if (this.mode === 'show' && this.data.id) {
      this.loadParticipants();
    }
  }

  /**
   * Checks for time conflicts before creating or editing an event.
   * If conflicts exist, the user can confirm that the event should still be saved.
   */
  save() {
    if (this.mode === 'create') {
      this.eventService.getConflictEvents(this.start, this.end).subscribe({
        next: conflicts => {
          if (conflicts.length > 0) {
            const dialogRef = this.dialog.open(ConflictDialog, {
              maxWidth: '560px',
              data: { conflicts },
            });

            dialogRef.afterClosed().subscribe(confirmed => {
              if (confirmed) {
                this.createEvent();
              }
            });
            return;
          }

          this.createEvent();
        },
        error: err => this.showError(err)
      });
    }

    if (this.mode === 'edit') {
      this.eventService.getConflictEvents(this.start, this.end).subscribe({
        next: conflicts => {
          // Do not warn about the event currently being edited.
          const otherConflicts = conflicts.filter(conflict => conflict.id !== this.data.id);

          if (otherConflicts.length > 0) {
            const dialogRef = this.dialog.open(ConflictDialog, {
              maxWidth: '560px',
              data: { conflicts: otherConflicts },
            });

            dialogRef.afterClosed().subscribe(confirmed => {
              if (confirmed) {
                this.saveEditedEvent();
              }
            });
            return;
          }

          this.saveEditedEvent();
        },
        error: err => this.showError(err)
      });
    }
  }

  /**
   * Creates a new manually entered event after conflict handling has finished.
   */
  createEvent() {
    this.eventService.createManualEvent({
      name: this.title,
      kosten: this.kosten ?? 0,
      tripId: this.tripId,
      start: this.start,
      end: this.end,
    }).subscribe({
      next: (response) => {
      if (this.invitations.length === 0) {
      this.dialogRef.close({ action: 'created', event: response });
      return;
    }

    const inviteCalls = this.invitations.map(friend =>
      this.eventService.inviteFriendToEvent(response.id, friend.id)
    );

    forkJoin(inviteCalls).subscribe({
      next: () => {
        this.reportInvitesSent();
        this.dialogRef.close({ action: 'created', event: response });
      },
      error: () => {
        this.feedback.error('Some invites could not be sent');
        this.dialogRef.close({ action: 'created', event: response });
      }
    });
    },
    error: (err) => {
      this.dialogRef.close({ action: 'error', message: err?.error || 'Event konnte nicht erstellt werden.' });
    }
    });
  }

  /**
   * Saves changes to an existing event after conflict handling has finished.
   */
  saveEditedEvent() {
    this.eventService.editEvent({
      id: this.data.id!,
      name: this.title,
      kosten: this.kosten ?? 0,
      tripId: this.tripId,
      start: this.start,
      end: this.end,
    }).subscribe({
      next: (response) => {
        if (this.invitations.length === 0) {
          this.dialogRef.close({ action: 'saved', event: response });
          return;
        }

        const inviteCalls = this.invitations.map(friend =>
          this.eventService.inviteFriendToEvent(this.data.id!, friend.id)
        );

        forkJoin(inviteCalls).subscribe({
          next: () => {
            this.reportInvitesSent();
            this.dialogRef.close({ action: 'saved', event: response });
          },
          error: () => {
            this.feedback.error('Some invites could not be sent');
            this.dialogRef.close({ action: 'saved', event: response });
          }
        });
      },
      error: () => this.showError()
    });
  }


  /**
   * closes the dialog without any actions
   */
  close() {
    this.dialogRef.close();
  }

  /**
   * closes the dialog eith the action 'delete'
   */
  delete() {
    this.eventService.deleteEvent(this.data.id!).subscribe({
      next: () => this.dialogRef.close({ action: 'deleted', id: this.data.id }),
      error: () => this.showError()
    });
  }

  /**
   * closes the dialog with the action 'edit'
   */
  edit(){
    this.dialogRef.close({ action: 'edit' });
  }

  /**
   * loads all the trips from the database
   */
  loadTrips() {
    this.tripService.getAllTrips().subscribe({
      next: trips => {

        this.trips = trips;

        const trip = this.trips.find(
          t => t.id === this.tripId
        );

        this.tripName = trip
          ? trip.name
          : 'Kein Trip';

      }
    });
  }

  loadFriends(){
    this.friendsService.getFriends().subscribe({
      next: friends => {
        this.friends = friends;
      }
    })
  }

  loadParticipants() {
    this.eventService.getParticipants(this.data.id!).subscribe({
      next: (participants) => this.participants = participants
    });
  }

  showError(err?: { error?: string }) {
    this.errorMessage = err?.error || 'Ein Fehler ist aufgetreten.';
  }

  /** Confirms sent event invites with a toast and one bell notification per friend. */
  private reportInvitesSent() {
    const eventName = this.title || 'the event';
    for (const friend of this.invitations) {
      this.notificationService.add(`${friend.username} was invited to the event "${eventName}"`);
    }
    const names = this.invitations.map(friend => friend.username).join(', ');
    this.feedback.success(`Invite sent to ${names}`);
  }

  inviteFriend(friend: Friend) {
    if (!this.invitations.some(f => f.id === friend.id)) {
      this.invitations = [...this.invitations, friend];
    }
  }

  removeInvitation(friend: Friend) {
    this.invitations = this.invitations.filter(f => f.id !== friend.id);
  }

  normalizeKosten(): void {
    if (typeof this.kosten === 'string') {
      const parsed = parseFloat((this.kosten as string).replace(',', '.'));
      this.kosten = isNaN(parsed) ? undefined : parsed;
    }
  }

  get isOwner(): boolean {
    if (this.mode === 'create') {
      return true;
    }
    return this.data.creatorUsername === localStorage.getItem('sepUser');
  }

  isParticipantOwner(username: string): boolean {
    return this.data.creatorUsername === username;
  }

  isParticipant(username :string): boolean {
    return this.participants.some(participant => participant.username === username);
  }

  removeParticipant(username: string){
    this.participants = this.participants.filter(
      participant => participant.username !== username
    );
  }

  removeFriendFromEvent(participant: UserEventDTO){
    const friendId = participant.userId;
    const eventId = this.data.id!;
    this.eventService.removeFriendFromEvent(eventId, friendId).subscribe({
      next: () => {
        this.removeParticipant(participant.username);
      },
      error: (err)=> {
        this.errorMessage = "Error: " + err.message;
      }
    })

  }

}
