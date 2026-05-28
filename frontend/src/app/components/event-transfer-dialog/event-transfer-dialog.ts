import {Component, inject, Inject} from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import {FormsModule} from '@angular/forms';
import {EventService} from '../../../event-adding-manual/service/event.service';
import {Trip, TripService} from '../../TripManagement/services/trip.service';

@Component({
  selector: 'app-event-transfer-dialog',
  imports: [FormsModule],
  templateUrl: './event-transfer-dialog.html',
  styleUrl: './event-transfer-dialog.css',
})
export class EventTransferDialog {
  mode: 'import' | 'export' = 'import';
  file?: File;
  fileName = '';
  eventService = inject(EventService);
  tripId: number | undefined;
  tripService = inject(TripService);
  trips: Trip[] = [];

  constructor(private dialogRef: MatDialogRef<EventTransferDialog>, @Inject(MAT_DIALOG_DATA) public data: any) {
    if (data) {
      this.mode = data.mode;

    }
  }

  ngOnInit() {
    this.loadTrips();
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;

    if (!input.files?.length) {
      return;
    }

    const file = input.files[0];

    if (!this.isEmlFile(file)) {
      console.error('Nur .eml-Dateien erlaubt');
      this.file = undefined;
      this.fileName = '';
      return;
    }

    this.file = file;
    this.fileName = file.name;
  }

  onFileDrop(event: DragEvent) {
    event.preventDefault();

    const files = event.dataTransfer?.files;

    if (!files?.length) {
      return;
    }

    const file = files[0];

    if (!this.isEmlFile(file)) {
      console.error('Nur .eml-Dateien erlaubt');
      this.file = undefined;
      this.fileName = '';
      return;
    }

    this.file = file;
    this.fileName = file.name;
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
  }

  uploadFile() {
    if (!this.file) {
      console.error('Keine .eml-Datei ausgewählt');
      return;
    }

    const importId = Date.now();

    const formData = new FormData();
    formData.append('MailFile', this.file);

    console.log(this.file);
    console.log(importId);

    if(!this.tripId){
      return;
    }
    this.eventService.importEvents(formData, this.tripId).subscribe({
      next: (event) => {
        this.dialogRef.close(event);
      },
      error: (err) => {
        console.error('Import fehlgeschlagen', err);
      }
    });
  }

  downloadEvents() {
    this.eventService.exportEvents().subscribe(blob => {

      const url = window.URL.createObjectURL(blob);

      const a = document.createElement('a');

      a.href = url;
      a.download = 'events.ics';

      a.click();

      window.URL.revokeObjectURL(url);

    });
  }

  private isEmlFile(file: File): boolean {
    return file.name.toLowerCase().endsWith('.eml');
  }

  loadTrips() {
    this.tripService.getAllTrips().subscribe({
      next: trips => {
        this.trips = trips;
      },
      error: err => {
        console.error('Trips konnten nicht geladen werden:', err);
      }
    });
  }

  close(){
    this.dialogRef.close();
  }
}

