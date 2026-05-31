import { Component, Inject, inject } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { MAT_DIALOG_DATA, type MatDialogRef } from "@angular/material/dialog";
import {
	type Trip,
	TripService,
} from "../../TripManagement/services/trip.service";

@Component({
	selector: "app-event-dialog",
	standalone: true,
	imports: [FormsModule],
	templateUrl: "./event-dialog.html",
	styleUrl: "./event-dialog.css",
})
export class EventDialog {
	title: string = "";
	kosten: number | undefined;
	tripId: number | undefined;
	tripName = "";
	start: string = "";
	end: string = "";
	tripService = inject(TripService);
	trips: Trip[] = [];

	mode: "create" | "edit" | "show" = "create";

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
		console.log("start end");
		console.log(this.start, this.end);
		this.dialogRef.close({
			action: "save",
			title: this.title,
			kosten: this.kosten,
			tripId: this.tripId,
			start: this.start,
			end: this.end,
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
		this.dialogRef.close({ action: "delete" });
	}

	/**
	 * closes the dialog with the action 'edit'
	 */
	edit() {
		this.dialogRef.close({ action: "edit" });
	}

	/**
	 * loads all the trips from the database
	 */
	loadTrips() {
		this.tripService.getAllTrips().subscribe({
			next: (trips) => {
				this.trips = trips;

				const trip = this.trips.find((t) => t.id === this.tripId);

				this.tripName = trip ? trip.name : "Kein Trip";
			},
		});
	}
}
