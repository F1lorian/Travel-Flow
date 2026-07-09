import {Component, inject, OnInit} from '@angular/core';
import { MatDialog, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
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
import { GmailMessagePreview, GmailService } from '../../services/gmail.service';
import { GMAIL_OAUTH_STATE_KEY } from '../gmail-import/gmail-import';

interface EventTransferDialogData {
  mode: 'import' | 'export';
}

type ImportSource = 'drag-drop' | 'imap' | 'gmail';

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
  gmailService = inject(GmailService);
  gmailConnected = false;
  gmailEmail: string | null = null;
  gmailStatusChecked = false;
  isCheckingGmailStatus = false;
  gmailMessages: GmailMessagePreview[] = [];
  selectedGmailMessageId?: string;
  isLoadingGmailMessages = false;
  isImportingGmail = false;

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

  /**
   * When the user switches to the Gmail source, check (once) whether a Gmail account is already
   * connected so the correct controls are shown.
   */
  onImportSourceChange() {
    if (this.importSource === 'gmail' && !this.gmailStatusChecked) {
      this.checkGmailStatus();
    }
  }

  /** Loads the current Gmail connection status for the logged-in user. */
  checkGmailStatus() {
    this.isCheckingGmailStatus = true;
    this.gmailService.getStatus().subscribe({
      next: status => {
        this.gmailConnected = status.connected;
        this.gmailEmail = status.email;
        this.gmailStatusChecked = true;
        this.isCheckingGmailStatus = false;
      },
      error: err => {
        this.isCheckingGmailStatus = false;
        this.showMessage(err.error || 'Gmail-Status konnte nicht geladen werden.');
      }
    });
  }

  /**
   * Starts the Google OAuth consent flow. A random state is stored so the /gmail-import redirect
   * can verify it, then the browser is sent to Google.
   */
  connectGmail() {
    const state = this.createOAuthState();
    sessionStorage.setItem(GMAIL_OAUTH_STATE_KEY, state);
    this.redirectTo(this.gmailService.buildConsentUrl(state));
  }

  /** Seam around the top-level navigation so it can be stubbed in tests. */
  protected redirectTo(url: string) {
    window.location.href = url;
  }

  /** Loads the most recent messages from the connected mailbox into the picker. */
  loadGmailMessages() {
    this.isLoadingGmailMessages = true;
    this.gmailMessages = [];
    this.selectedGmailMessageId = undefined;

    this.gmailService.getMessages().subscribe({
      next: messages => {
        this.gmailMessages = messages;
        this.selectedGmailMessageId = messages[0]?.id;
        this.isLoadingGmailMessages = false;
      },
      error: err => {
        this.isLoadingGmailMessages = false;
        this.showMessage(err.error || 'Gmail-Nachrichten konnten nicht geladen werden.');
      }
    });
  }

  /**
   * Parses the selected Gmail message on the backend and routes the result through the existing
   * conflict-resolution flow.
   */
  importSelectedGmailMessage() {
    if (!this.selectedGmailMessageId) {
      this.showMessage('Bitte zuerst eine Gmail-Nachricht auswählen.');
      return;
    }

    this.isImportingGmail = true;

    this.gmailService.parseMessage(this.selectedGmailMessageId).subscribe({
      next: parsedEvent => {
        this.isImportingGmail = false;
        this.handleParsedEvent(parsedEvent);
      },
      error: err => {
        this.isImportingGmail = false;
        this.showMessage(err.error || 'Gmail-Nachricht konnte nicht gelesen werden.');
      }
    });
  }

  private createOAuthState(): string {
    return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
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
