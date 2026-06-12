import {Component, inject, Inject} from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import {FormsModule} from '@angular/forms';
import {Trip, TripService} from '../../TripManagement/services/trip.service';
import {EventService} from '../../../event-adding-manual/service/event.service';

@Component({
  selector: 'app-event-dialog',
  standalone: true,
  imports: [
    FormsModule
  ],
  templateUrl: './event-dialog.html',
  styleUrl: './event-dialog.css',
})
export class EventDialog {
  title: string = "";
  kosten: number | undefined;
  tripId: number | undefined;
  tripName = '';
  start: string = "";
  end: string = "";
  tripService = inject(TripService);
  eventService = inject(EventService);
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
    console.log(data);
  }

  ngOnInit() {
    this.loadTrips();
  }

  /**
   * closes the dialog and gives the calendar the action 'save' as well as all the attributes for the event
   */
  save() {
    if (this.mode === 'create') {
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

    if (this.mode === 'edit') {
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
    this.eventService.deleteEvent(this.data.id).subscribe({
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

  showError(err?: any) {
    this.errorMessage = err?.error || 'Ein Fehler ist aufgetreten.';
  }


}
