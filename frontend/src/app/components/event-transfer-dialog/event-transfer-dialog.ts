import {Component, inject, OnInit} from '@angular/core';
import { MatDialog, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import {FormsModule} from '@angular/forms';
import {EventService} from '../../../event-adding-manual/service/event.service';
import {Trip, TripService} from '../../TripManagement/services/trip.service';
import {MessageDialog} from '../message-dialog/message-dialog';
import {ConflictDialog} from '../conflict-dialog/conflict-dialog';
import {EventPreviewDialog} from '../event-preview-dialog/event-preview-dialog';
import { ParsedEvent } from '../../../event-adding-manual/models/ParsedEvent';

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
   * Parses the selected .eml file, lets the user double-check and correct the extracted
   * values, checks the reviewed time for conflicts, and saves the imported event only
   * after the user confirms.
   */
  uploadFile() {
    if (!this.file) {
      this.showMessage('Keine .eml-Datei ausgewählt');
      return;
    }

    const formData = new FormData();
    formData.append('MailFile', this.file);


    this.eventService.parseEvent(formData, this.tripId).subscribe({
      next: (parsedEvent) => {
        const previewRef = this.dialog.open(EventPreviewDialog, {
          maxWidth: '560px',
          data: { parsedEvent },
        });

        previewRef.afterClosed().subscribe((reviewedEvent: ParsedEvent | undefined) => {
          if (!reviewedEvent) {
            return;
          }

          this.checkConflictsAndSave(reviewedEvent);
        });
      },
      error: (err) => {
        this.showMessage(err.error || 'Event konnte nicht gelesen werden!');
      }
    });
  }

  /**
   * Checks the reviewed event for time conflicts and saves it, asking the user to confirm
   * first when overlapping events exist.
   * @param reviewedEvent event values the user confirmed in the preview dialog
   */
  private checkConflictsAndSave(reviewedEvent: ParsedEvent) {
    this.eventService.getConflictEvents(reviewedEvent.start, reviewedEvent.end).subscribe({
      next: (conflicts) => {
        if (conflicts.length > 0) {
          const dialogRef = this.dialog.open(ConflictDialog, {
            maxWidth: '560px',
            data: { conflicts },
          });

          dialogRef.afterClosed().subscribe(confirmed => {
            if (confirmed) {
              this.saveParsedEvent(reviewedEvent);
            }
          });

          return;
        }

        this.saveParsedEvent(reviewedEvent);
      },
      error: (err) => {
        this.showMessage(err.error || 'Konfliktpruefung fehlgeschlagen.');
      }
    });
  }

  /**
   * Saves a previously parsed imported event and returns the saved database event to the calendar.
   * @param parsedEvent event values extracted from the uploaded .eml file
   */
  private saveParsedEvent(parsedEvent: ParsedEvent) {
    this.eventService.saveParsedEvent(parsedEvent, this.tripId).subscribe({
      next: (event) => {
        this.dialogRef.close(event);
      },
      error: (err) => {
        this.showMessage(err.error || 'Event konnte nicht erstellt werden!');
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
