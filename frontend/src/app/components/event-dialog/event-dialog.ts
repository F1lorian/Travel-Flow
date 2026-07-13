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
import {catchError, forkJoin, of} from 'rxjs';
import {NotificationService} from '../notification-menu/notification.service';
import {FeedbackService} from '../../services/feedback.service';
import {addDays, isAllDayRange} from '../../services/all-day';

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
  participantToRemove: UserEventDTO | null = null;

  mode: 'create' | 'edit' | 'show'= 'create';

  constructor() {
    if(this.data) {
      this.mode = this.data.mode || 'create';
      this.title = this.data.title || '';
      this.kosten = this.data.kosten || undefined;
      this.tripId = this.data.tripId || undefined;
      this.start = this.data.start || "";
      this.end = this.data.end || "";

      // All-day events arrive with an exclusive end (midnight after the last day);
      // the form works with the inclusive last day so users see the range they picked.
      this.allDay = isAllDayRange(this.start, this.end);
      if (this.allDay) {
        this.start = this.start.substring(0, 10) + 'T00:00';
        this.end = addDays(this.end, -1) + 'T00:00';
      }
    }
  }

  ngOnInit() {
    this.loadTrips();
    this.loadFriends();

    if ((this.mode === 'show' || this.mode === 'edit') && this.data.id) {
      this.loadParticipants();
    }
  }

  /**
   * Checks for time conflicts before creating or editing an event.
   * If conflicts exist, the user can confirm that the event should still be saved.
   */
  save() {
    const { start, end } = this.normalizedRange();

    if (this.mode === 'create') {
      this.eventService.getConflictEvents(start, end).subscribe({
        next: conflicts => {
          if (conflicts.length > 0) {
            const dialogRef = this.dialog.open(ConflictDialog, {
              maxWidth: '560px',
              data: { conflicts },
            });

            dialogRef.afterClosed().subscribe(confirmed => {
              if (confirmed) {
                this.createEvent(start, end);
              }
            });
            return;
          }

          this.createEvent(start, end);
        },
        error: err => this.showError(err)
      });
    }

    if (this.mode === 'edit') {
      this.eventService.getConflictEvents(start, end).subscribe({
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
                this.saveEditedEvent(start, end);
              }
            });
            return;
          }

          this.saveEditedEvent(start, end);
        },
        error: err => this.showError(err)
      });
    }
  }

  /**
   * The range that gets saved: all-day events are converted to their storage shape
   * (midnight start, exclusive midnight end after the last day) while timed events pass
   * through unchanged. The form fields stay untouched so a failed save cannot shift them.
   */
  private normalizedRange(): { start: string; end: string } {
    if (!this.allDay || !this.start) {
      return { start: this.start, end: this.end };
    }
    const startDay = this.start.substring(0, 10);
    let endDay = (this.end || this.start).substring(0, 10);
    if (endDay < startDay) {
      endDay = startDay;
    }
    return { start: `${startDay}T00:00`, end: `${addDays(endDay, 1)}T00:00` };
  }

  /**
   * Date-only view of the range for the all-day inputs. `start`/`end` stay canonical
   * (datetime-local format), so toggling all-day off restores the original times.
   */
  get startDay(): string {
    return this.start.substring(0, 10);
  }

  set startDay(value: string) {
    this.start = value ? `${value}T00:00` : '';
  }

  get endDay(): string {
    return this.end.substring(0, 10);
  }

  set endDay(value: string) {
    this.end = value ? `${value}T00:00` : '';
  }

  /**
   * Creates a new manually entered event after conflict handling has finished.
   */
  createEvent(start = this.start, end = this.end) {
    this.eventService.createManualEvent({
      name: this.title,
      kosten: this.kosten ?? 0,
      tripId: this.tripId,
      start,
      end,
    }).subscribe({
      next: (response) => {
      if (this.invitations.length === 0) {
      this.dialogRef.close({ action: 'created', event: response });
      return;
    }
      const inviteCalls = this.invitations.map(friend =>
          this.eventService.inviteFriendToEvent(response.id, friend.id).pipe(
            catchError(() => of(null))
          )
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
  saveEditedEvent(start = this.start, end = this.end) {
    this.eventService.editEvent({
      id: this.data.id!,
      name: this.title,
      kosten: this.kosten ?? 0,
      tripId: this.tripId,
      start,
      end,
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

  get acceptedParticipants(): UserEventDTO[] {
    return this.participants.filter(p => p.status === 'ACCEPTED');
  }

  /** Accepted participants without the viewer — the show dialog lists only the others. */
  get attendingOthers(): UserEventDTO[] {
    const currentUsername = localStorage.getItem('sepUser');
    return this.participants.filter(
      p => p.status === 'ACCEPTED' && p.username !== currentUsername
    );
  }

  get attendingFriends(): UserEventDTO[] {
    return this.participants.filter(
      p => p.status === 'ACCEPTED' && !this.isParticipantOwner(p.username)
    );
  }

  getParticipantStatus(username: string): 'ACCEPTED' | 'PENDING' | 'DECLINED' | null {
    const participant = this.participants.find(p => p.username === username);
    return participant ? participant.status : null;
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

  leaveEvent(): void {
    const currentUsername = localStorage.getItem('sepUser');
    const self = this.participants.find(p => p.username === currentUsername);
    if (!self) {
      return;
    }
    this.openRemoveConfirm(self);
  }

  openRemoveConfirm(participant: UserEventDTO): void {
    this.participantToRemove = participant;
  }

  cancelRemoveConfirm(): void {
    this.participantToRemove = null;
  }

  confirmRemoveParticipant(): void {
    if (!this.participantToRemove) {
      return;
    }
    this.removeFriendFromEvent(this.participantToRemove);
    this.participantToRemove = null;
  }

}
