import {Component, inject, Inject} from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import {FormsModule} from '@angular/forms';
import {Trip, TripService} from '../../TripManagement/services/trip.service';

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
  tripId?: number;
  tripName = '';
  start: string = "";
  end: string = "";
  allDay: boolean = false;
  tripService = inject(TripService);
  trips: Trip[] = [];


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

  save() {
    console.log("start end");
    console.log(this.start, this.end);
    this.dialogRef.close({action: 'save',title: this.title, kosten: this.kosten, tripId: this.tripId, start:this.start, end: this.end});
  }

  close() {
    this.dialogRef.close();
  }

  delete() {
    this.dialogRef.close({ action: 'delete' });
  }

  edit(){
    this.dialogRef.close({ action: 'edit' });
  }

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

}
