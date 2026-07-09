import {Component, inject, OnInit} from '@angular/core';
import { MatDialog, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import {FormsModule} from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
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
import { AddEventResponse } from '../../../event-adding-manual/models/AddEventResponse';
import { GmailMessagePreview, GmailService } from '../../services/gmail.service';
import { EventImportReview, ReviewItem } from '../event-import-review/event-import-review';
import { GMAIL_OAUTH_STATE_KEY } from '../gmail-import/gmail-import';

/** Whether imported Gmail events are saved directly or reviewed first. */
type GmailImportMode = 'auto' | 'review';

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
  selectedGmailIds = new Set<string>();
  gmailImportMode: GmailImportMode = 'review';
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
    this.selectedGmailIds.clear();

    this.gmailService.getMessages().subscribe({
      next: messages => {
        this.gmailMessages = messages;
        // Preselect everything so the common "import all" case is one click.
        this.selectedGmailIds = new Set(messages.map(message => message.id));
        this.isLoadingGmailMessages = false;
      },
      error: err => {
        this.isLoadingGmailMessages = false;
        this.showMessage(err.error || 'Gmail-Nachrichten konnten nicht geladen werden.');
      }
    });
  }

  /** Whether the given message is currently checked for import. */
  isGmailSelected(id: string): boolean {
    return this.selectedGmailIds.has(id);
  }

  /** Toggles a single message in/out of the import selection. */
  toggleGmailSelection(id: string) {
    if (this.selectedGmailIds.has(id)) {
      this.selectedGmailIds.delete(id);
    } else {
      this.selectedGmailIds.add(id);
    }
  }

  /** Number of messages currently selected for import. */
  get selectedGmailCount(): number {
    return this.selectedGmailIds.size;
  }

  /** True when every loaded message is selected. */
  get allGmailSelected(): boolean {
    return this.gmailMessages.length > 0 && this.selectedGmailIds.size === this.gmailMessages.length;
  }

  /** Selects all loaded messages, or clears the selection when all are already selected. */
  toggleSelectAllGmail() {
    if (this.allGmailSelected) {
      this.selectedGmailIds.clear();
    } else {
      this.selectedGmailIds = new Set(this.gmailMessages.map(message => message.id));
    }
  }

  /**
   * Imports the selected Gmail messages. In 'auto' mode the backend keeps only messages that
   * actually contain an event (Gemini detection) and those are saved directly; in 'review' mode
   * every message is parsed and the user picks which ones to keep.
   */
  importSelectedGmailMessages() {
    const ids = [...this.selectedGmailIds];

    if (ids.length === 0) {
      this.showMessage('Bitte zuerst mindestens eine Gmail-Nachricht auswählen.');
      return;
    }

    this.isImportingGmail = true;

    if (this.gmailImportMode === 'auto') {
      this.autoImportGmail(ids);
    } else {
      this.reviewImportGmail(ids);
    }
  }

  /**
   * Auto mode: asks the backend for the events among the selected messages (non-event mails are
   * skipped there) and saves them directly.
   */
  private autoImportGmail(ids: string[]) {
    this.gmailService.autoParse(ids).subscribe({
      next: events => {
        if (events.length === 0) {
          this.isImportingGmail = false;
          this.showMessage('In den ausgewählten Nachrichten wurden keine Termine gefunden.');
          return;
        }
        this.saveParsedEvents(events, 0);
      },
      error: err => {
        this.isImportingGmail = false;
        this.showMessage(err.error || 'Gmail-Nachrichten konnten nicht importiert werden.');
      },
    });
  }

  /** Review mode: parses every selected message, then lets the user pick which ones to keep. */
  private reviewImportGmail(ids: string[]) {
    const subjectById = new Map(this.gmailMessages.map(message => [message.id, message.subject]));
    const parseCalls = ids.map(id =>
      this.gmailService.parseMessage(id).pipe(
        map((event): ReviewItem => ({ subject: subjectById.get(id) ?? '', event })),
        catchError(() => of(null)),
      ),
    );

    forkJoin(parseCalls).subscribe({
      next: results => {
        this.isImportingGmail = false;
        const parsed = results.filter((item): item is ReviewItem => item !== null);
        const failedToParse = results.length - parsed.length;

        if (parsed.length === 0) {
          this.showMessage('Keine der ausgewählten Nachrichten konnte gelesen werden.');
          return;
        }

        this.reviewThenSave(parsed, failedToParse);
      },
      error: err => {
        this.isImportingGmail = false;
        this.showMessage(err.error || 'Gmail-Nachrichten konnten nicht gelesen werden.');
      },
    });
  }

  /** Opens the review dialog and saves whatever the user confirms. */
  private reviewThenSave(items: ReviewItem[], failedToParse: number) {
    const dialogRef = this.dialog.open(EventImportReview, {
      maxWidth: '600px',
      data: { items },
    });

    dialogRef.afterClosed().subscribe((confirmed: ParsedEvent[] | undefined) => {
      if (confirmed && confirmed.length > 0) {
        this.saveParsedEvents(confirmed, failedToParse);
      }
    });
  }

  /**
   * Saves several parsed events, then closes the dialog with the saved events so the calendar can
   * add them. Surfaces a summary when some messages failed to parse or save.
   */
  private saveParsedEvents(events: ParsedEvent[], failedToParse: number) {
    this.isImportingGmail = true;

    const saveCalls = events.map(event =>
      this.eventService.saveParsedEvent(event, this.tripId).pipe(catchError(() => of(null))),
    );

    forkJoin(saveCalls).subscribe({
      next: results => {
        this.isImportingGmail = false;
        const saved = results.filter((event): event is AddEventResponse => event !== null);
        const failed = failedToParse + (results.length - saved.length);

        if (saved.length === 0) {
          this.showMessage('Kein Event konnte importiert werden.');
          return;
        }

        if (failed > 0) {
          this.showMessage(`${saved.length} Event(s) importiert, ${failed} fehlgeschlagen.`);
        }

        this.dialogRef.close(saved);
      },
      error: err => {
        this.isImportingGmail = false;
        this.showMessage(err.error || 'Events konnten nicht importiert werden.');
      },
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
