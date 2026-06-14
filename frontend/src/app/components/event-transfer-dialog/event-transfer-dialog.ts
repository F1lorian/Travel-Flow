import {Component, inject, OnInit} from '@angular/core';
import { MatDialog, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import {FormsModule} from '@angular/forms';
import {EventService} from '../../../event-adding-manual/service/event.service';
import {Trip, TripService} from '../../TripManagement/services/trip.service';
import {MessageDialog} from '../message-dialog/message-dialog';

interface EventTransferDialogData {
  mode: 'import' | 'export';
}

@Component({
  selector: 'app-event-transfer-dialog',
  imports: [FormsModule],
  templateUrl: './event-transfer-dialog.html',
  styleUrl: './event-transfer-dialog.css',
})
export class EventTransferDialog implements OnInit {
  private readonly dialogRef = inject(MatDialogRef<EventTransferDialog>);
  readonly data = inject<EventTransferDialogData>(MAT_DIALOG_DATA);
  mode: 'import' | 'export' = 'import';
  file?: File;
  fileName = '';
  eventService = inject(EventService);
  tripId?: number;
  tripService = inject(TripService);
  trips: Trip[] = [];
  dialog = inject(MatDialog);

  constructor() {
    if (this.data) {
      this.mode = this.data.mode;

    }
  }

  ngOnInit() {
    this.loadTrips();
  }

  /**
   * saves the file that is selected from the file explorer
   * @param event is the selected file
   */
  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;

    if (!input.files?.length) {
      return;
    }

    const file = input.files[0];

    if (!this.isEmlFile(file)) {
      this.showMessage('Nur .eml-Dateien erlaubt');
      this.file = undefined;
      this.fileName = '';
      return;
    }

    this.file = file;
    this.fileName = file.name;
  }

  /**
   * saves the file that is selected per drag and drop
   * @param event is the selected event
   */
  onFileDrop(event: DragEvent) {
    event.preventDefault();

    const files = event.dataTransfer?.files;

    if (!files?.length) {
      return;
    }

    const file = files[0];

    if (!this.isEmlFile(file)) {
      this.showMessage('Nur .eml-Dateien erlaubt');
      this.file = undefined;
      this.fileName = '';
      return;
    }

    this.file = file;
    this.fileName = file.name;
  }

  /**
   * prevents the browsers default action, when a file is dragged over the browser
   * @param event is the selected event
   */
  onDragOver(event: DragEvent) {
    event.preventDefault();
  }

  /**
   * uploads the file to the backend and returns the created event back to the calendar
   */
  uploadFile() {
    if (!this.file) {
      this.showMessage('Keine .eml-Datei ausgewählt');
      return;
    }

    const formData = new FormData();
    formData.append('MailFile', this.file);


    this.eventService.importEvents(formData, this.tripId).subscribe({
      next: (event) => {
        this.dialogRef.close(event);
      },
      error: (err) => {
        this.showMessage(
          err.error || "Event konnte nicht erstellt werden!"
        );
      }
    });
  }

  /**
   * lets the user download the .ics file with all of the events
   */
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

  /**
   * loads all trips from the backend
   */
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

  /**
   * shows the user messages, potential errors from the backend
   * @param message is the message
   */
  showMessage(message: string){
    this.dialog.open(MessageDialog, {data: {message: message}})
  }

  /**
   * closes the dialog
   */
  close(){
    this.dialogRef.close();
  }

}

