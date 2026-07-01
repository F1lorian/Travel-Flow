import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { GmailImportService } from '../../services/gmail-import.service';
import { ExtractedEvent, GmailImportRequest } from '../../models/gmail-import.models';

/** Editable form model backing the confirmation screen. */
interface EventForm {
  title: string;
  description: string;
  date: string;
  startTime: string;
  endTime: string;
  timeZone: string;
  location: string;
}

/**
 * Gmail import page. It walks the user through: connect Gmail (OAuth) → scan recent
 * emails → review/edit the extracted event on a form → create the calendar event.
 * Nothing is created until the user clicks "Create Event".
 */
@Component({
  selector: 'app-gmail-import',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './gmail-import.html',
  styleUrl: './gmail-import.css'
})
export class GmailImportComponent implements OnInit {
  private readonly gmailService = inject(GmailImportService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  connected = signal(false);
  emailAddress = signal<string | null>(null);
  candidates = signal<ExtractedEvent[]>([]);
  selected = signal<ExtractedEvent | null>(null);

  isBusy = signal(false);
  hasScanned = signal(false);
  errorMessage = signal('');
  successMessage = signal('');

  // Plain object (not a signal) so it can be two-way bound with ngModel in the form.
  form: EventForm = this.emptyForm();

  ngOnInit(): void {
    // Google redirects back here with ?code=... after the user grants access.
    const code = this.route.snapshot.queryParamMap.get('code');
    const error = this.route.snapshot.queryParamMap.get('error');
    if (error) {
      this.errorMessage.set('Gmail connection was cancelled or denied.');
      this.clearOAuthQueryParams();
    }
    if (code) {
      this.completeConnection(code);
    } else {
      this.loadStatus();
    }
  }

  /** Starts the OAuth flow by redirecting the browser to Google's consent screen. */
  connectGmail(): void {
    this.resetMessages();
    this.isBusy.set(true);
    this.gmailService.getAuthUrl().subscribe({
      next: (response) => (window.location.href = response.authorizationUrl),
      error: () => {
        this.isBusy.set(false);
        this.errorMessage.set('Could not start Gmail connection. Please try again later.');
      }
    });
  }

  /** Removes the Gmail connection. */
  disconnectGmail(): void {
    this.resetMessages();
    this.gmailService.disconnect().subscribe({
      next: () => {
        this.connected.set(false);
        this.emailAddress.set(null);
        this.candidates.set([]);
        this.selected.set(null);
        this.hasScanned.set(false);
      },
      error: () => this.errorMessage.set('Could not disconnect Gmail.')
    });
  }

  /** Scans recent emails and lists the events that were found. */
  scanEmails(): void {
    this.resetMessages();
    this.isBusy.set(true);
    this.selected.set(null);
    this.gmailService.scan().subscribe({
      next: (events) => {
        this.candidates.set(events);
        this.hasScanned.set(true);
        this.isBusy.set(false);
      },
      error: (err) => {
        this.isBusy.set(false);
        this.errorMessage.set(this.readError(err, 'Could not read your emails. Please try again.'));
      }
    });
  }

  /** Opens the confirmation form pre-filled with the chosen event's extracted values. */
  reviewEvent(event: ExtractedEvent): void {
    this.resetMessages();
    this.selected.set(event);
    this.form = {
      title: event.title ?? '',
      description: event.description ?? '',
      date: event.date ?? '',
      startTime: this.toInputTime(event.startTime),
      endTime: this.toInputTime(event.endTime),
      timeZone: event.timeZone ?? '',
      location: event.location ?? ''
    };
  }

  /** Cancels the review without creating anything. */
  cancelReview(): void {
    this.selected.set(null);
  }

  /** Creates the calendar event from the confirmed form values. */
  createEvent(): void {
    this.resetMessages();
    if (!this.form.date) {
      this.errorMessage.set('Please provide a date before creating the event.');
      return;
    }
    this.isBusy.set(true);
    const request: GmailImportRequest = {
      title: this.form.title.trim() || 'Untitled event',
      description: this.emptyToNull(this.form.description),
      date: this.form.date,
      startTime: this.emptyToNull(this.form.startTime),
      endTime: this.emptyToNull(this.form.endTime),
      timeZone: this.emptyToNull(this.form.timeZone),
      location: this.emptyToNull(this.form.location)
    };

    this.gmailService.importEvent(request).subscribe({
      next: (created) => {
        this.isBusy.set(false);
        this.successMessage.set(`${created.message}: "${created.title}".`);
        this.removeSelectedFromCandidates();
        this.selected.set(null);
      },
      error: (err) => {
        this.isBusy.set(false);
        this.errorMessage.set(this.readError(err, 'Could not create the event. Please try again.'));
      }
    });
  }

  private completeConnection(code: string): void {
    this.isBusy.set(true);
    this.gmailService.connect(code).subscribe({
      next: (status) => {
        this.isBusy.set(false);
        this.connected.set(status.connected);
        this.emailAddress.set(status.emailAddress);
        this.successMessage.set('Gmail connected successfully.');
        this.clearOAuthQueryParams();
      },
      error: (err) => {
        this.isBusy.set(false);
        this.errorMessage.set(this.readError(err, 'Could not connect Gmail. Please try again.'));
        this.clearOAuthQueryParams();
      }
    });
  }

  private loadStatus(): void {
    this.gmailService.getStatus().subscribe({
      next: (status) => {
        this.connected.set(status.connected);
        this.emailAddress.set(status.emailAddress);
      },
      error: () => this.connected.set(false)
    });
  }

  private removeSelectedFromCandidates(): void {
    const current = this.selected();
    if (!current) return;
    this.candidates.set(
      this.candidates().filter((event) => event.sourceMessageId !== current.sourceMessageId)
    );
  }

  private clearOAuthQueryParams(): void {
    // Drop ?code/?error from the URL so a page refresh does not re-trigger the exchange.
    this.router.navigate([], { queryParams: {}, replaceUrl: true });
  }

  private resetMessages(): void {
    this.errorMessage.set('');
    this.successMessage.set('');
  }

  // The Gmail LocalTime is serialized as "HH:mm:ss"; an <input type="time"> wants "HH:mm".
  private toInputTime(time: string | null): string {
    if (!time) return '';
    return time.length >= 5 ? time.substring(0, 5) : time;
  }

  private emptyToNull(value: string): string | null {
    const trimmed = value.trim();
    return trimmed.length === 0 ? null : trimmed;
  }

  private readError(err: unknown, fallback: string): string {
    const message = (err as { error?: { message?: string } })?.error?.message;
    return message ?? fallback;
  }

  private emptyForm(): EventForm {
    return {
      title: '',
      description: '',
      date: '',
      startTime: '',
      endTime: '',
      timeZone: '',
      location: ''
    };
  }
}
