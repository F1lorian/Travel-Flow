import {Component, inject, OnInit} from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import {FormsModule} from '@angular/forms';
import {Trip, TripService} from '../../TripManagement/services/trip.service';
import {EventService} from '../../../event-adding-manual/service/event.service';
import {ConflictDialog} from '../conflict-dialog/conflict-dialog';

interface EventDialogData {
  mode?: 'create' | 'edit' | 'show';
  id?: string;
  title?: string;
  kosten?: number;
  tripId?: number;
  start?: string;
  end?: string;
}

@Component({
  selector: 'app-event-dialog',
  standalone: true,
  imports: [
    FormsModule
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
  start: string = "";
  end: string = "";
  tripService = inject(TripService);
  eventService = inject(EventService);
  dialog = inject(MatDialog);
  trips: Trip[] = [];
  errorMessage: string = "";


  mode: 'create' | 'edit' | 'show'= 'create';

  constructor(private dialogRef: MatDialogRef<EventDialog>, @Inject(MAT_DIALOG_DATA) public data: any) {
    if(data) {
      this.mode = data.mode || 'create';
      this.title = data.title || '';
      this.kosten = data.kosten || undefined;
      this.tripId = data.tripId || undefined;
      this.start = data.start || "";
      this.end = data.end || "";
    }
    console.log("data dialog");
    console.log(this.data);
  }

  ngOnInit() {
    this.loadTrips();
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
      next: (response) => this.dialogRef.close({ action: 'created', event: response }),
      error: (err) => this.showError(err)
    });
  }

  /**
   * Saves changes to an existing event after conflict handling has finished.
   */
  saveEditedEvent() {
    this.eventService.editEvent({
      id: this.data.id,
      name: this.title,
      kosten: this.kosten ?? 0,
      tripId: this.tripId,
      start: this.start,
      end: this.end,
    }).subscribe({
      next: (response) => this.dialogRef.close({ action: 'saved', event: response }),
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

  showError(err?: { error?: string }) {
    this.errorMessage = err?.error || 'Ein Fehler ist aufgetreten.';
  }


}
