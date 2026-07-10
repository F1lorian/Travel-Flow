import {Component, inject, OnInit} from '@angular/core';
import {MatDialog, MatDialogRef, MAT_DIALOG_DATA, MatDialogContent, MatDialogActions} from '@angular/material/dialog';
import {FormsModule} from '@angular/forms';
import {
  EventService,
  ImapMailPreview,
  ImapMailSelectionRequest,
  ImapMailboxRequest
} from '../../../event-adding-manual/service/event.service';
import {Trip, TripService} from '../../TripManagement/services/trip.service';
import {MessageDialog} from '../message-dialog/message-dialog';
import {ConflictDialog} from '../conflict-dialog/conflict-dialog';
import { ParsedEvent } from '../../../event-adding-manual/models/ParsedEvent';
import {MatIcon} from '@angular/material/icon';
import {MatButton} from '@angular/material/button';

interface EventTransferDialogData {
  mode: 'import' | 'export';
}

type ImportSource = 'drag-drop' | 'imap' | 'gmail';

@Component({
  selector: 'app-event-transfer-dialog',
  imports: [FormsModule, MatDialogContent, MatDialogActions, MatIcon, MatButton],
  templateUrl: './event-transfer-dialog.html',
  styleUrl: './event-transfer-dialog.css',
})
export class EventTransferDialog implements OnInit {
  private readonly dialogRef = inject(MatDialogRef<EventTransferDialog>);
  readonly data = inject<EventTransferDialogData>(MAT_DIALOG_DATA);
  mode: 'import' | 'export' = 'import';
  importSource: ImportSource = 'drag-drop';
  file?: File;
  fileName = '';
  eventService = inject(EventService);
  tripId?: number;
  tripService = inject(TripService);
  trips: Trip[] = [];
  dialog = inject(MatDialog);
  imapHost = '';
  imapPort = 993;
  imapUsername = '';
  imapPassword = '';
  imapFolder = 'INBOX';
  imapLimit = 20;
  imapMessages: ImapMailPreview[] = [];
  selectedImapUid?: number;
  isLoadingImapMessages = false;

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
   * Parses the selected .eml file, checks the parsed time for conflicts, and saves the
   * imported event only after the user confirms possible conflicts.
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
        this.handleParsedEvent(parsedEvent);
      },
      error: (err) => {
        this.showMessage(err.error || 'Event konnte nicht gelesen werden!');
      }
    });
  }

  loadImapMessages() {
    const request = this.buildImapMailboxRequest();

    if (!request) {
      return;
    }

    this.isLoadingImapMessages = true;
    this.imapMessages = [];
    this.selectedImapUid = undefined;

    this.eventService.getImapMessages(request).subscribe({
      next: messages => {
        this.imapMessages = messages;
        this.selectedImapUid = messages[0]?.uid;
        this.isLoadingImapMessages = false;
      },
      error: err => {
        this.isLoadingImapMessages = false;
        this.showMessage(err.error || 'IMAP-Nachrichten konnten nicht geladen werden.');
      }
    });
  }

  importSelectedImapMessage() {
    const mailboxRequest = this.buildImapMailboxRequest();

    if (!mailboxRequest || this.selectedImapUid === undefined) {
      this.showMessage('Bitte zuerst eine IMAP-Nachricht auswÃ¤hlen.');
      return;
    }

    const request: ImapMailSelectionRequest = {
      ...mailboxRequest,
      uid: Number(this.selectedImapUid),
    };

    this.eventService.parseImapMessage(request).subscribe({
      next: parsedEvent => this.handleParsedEvent(parsedEvent),
      error: err => this.showMessage(err.error || 'IMAP-Nachricht konnte nicht gelesen werden.')
    });
  }

  private handleParsedEvent(parsedEvent: ParsedEvent) {
    this.eventService.getConflictEvents(parsedEvent.start, parsedEvent.end).subscribe({
      next: (conflicts) => {
        if (conflicts.length > 0) {
          const dialogRef = this.dialog.open(ConflictDialog, {
            maxWidth: '560px',
            data: { conflicts },
          });

          dialogRef.afterClosed().subscribe(confirmed => {
            if (confirmed) {
              this.saveParsedEvent(parsedEvent);
            }
          });

          return;
        }

        this.saveParsedEvent(parsedEvent);
      },
      error: (err) => {
        this.showMessage(err.error || 'Konfliktpruefung fehlgeschlagen.');
      }
    });
  }

  private buildImapMailboxRequest(): ImapMailboxRequest | null {
    if (!this.imapHost.trim() || !this.imapUsername.trim() || !this.imapPassword) {
      this.showMessage('Bitte IMAP-Host, Benutzername und Passwort ausfÃ¼llen.');
      return null;
    }

    return {
      host: this.imapHost.trim(),
      port: Number(this.imapPort) || 993,
      username: this.imapUsername.trim(),
      password: this.imapPassword,
      folder: this.imapFolder.trim() || 'INBOX',
      limit: Number(this.imapLimit) || 20,
    };
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
