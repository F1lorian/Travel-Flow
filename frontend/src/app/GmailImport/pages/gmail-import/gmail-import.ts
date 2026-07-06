import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { GmailImportService } from '../../services/gmail-import.service';
import { ExtractedEvent, GmailImportRequest } from '../../models/gmail-import.models';
import { Trip, TripService } from '../../../TripManagement/services/trip.service';

/** Value used by the trip <select> to signal the user wants to create a brand new trip. */
const CREATE_NEW_TRIP = 'new';

/** Editable form model backing the confirmation screen. */
interface EventForm {
  title: string;
  description: string;
  date: string;
  startTime: string;
  endTime: string;
  /** Cost of the event in EUR; may be a string while typing before it is normalized. */
  kosten: number | string | undefined;
  /** Selected trip: an existing trip id, {@link CREATE_NEW_TRIP}, or undefined for no trip. */
  tripChoice: number | typeof CREATE_NEW_TRIP | undefined;
  /** Name entered when creating a new trip inline (only used when tripChoice is CREATE_NEW_TRIP). */
  newTripName: string;
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
  private readonly tripService = inject(TripService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly CREATE_NEW_TRIP = CREATE_NEW_TRIP;

  connected = signal(false);
  emailAddress = signal<string | null>(null);
  candidates = signal<ExtractedEvent[]>([]);
  selected = signal<ExtractedEvent | null>(null);
  trips = signal<Trip[]>([]);

  isBusy = signal(false);
  hasScanned = signal(false);
  errorMessage = signal('');
  successMessage = signal('');

  // Plain object (not a signal) so it can be two-way bound with ngModel in the form.
  form: EventForm = this.emptyForm();

  ngOnInit(): void {
    this.loadTrips();
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

  /** Loads the user's trips so they can be attached to (or created for) the imported event. */
  private loadTrips(): void {
    this.tripService.getAllTrips().subscribe({
      next: (trips) => this.trips.set(trips),
      error: () => this.trips.set([])
    });
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
      kosten: undefined,
      tripChoice: undefined,
      newTripName: ''
    };
  }

  /** Normalizes the free-typed cost into a number (accepting a comma as decimal separator). */
  normalizeKosten(): void {
    if (typeof this.form.kosten === 'string') {
      const parsed = parseFloat(this.form.kosten.replace(',', '.'));
      this.form.kosten = isNaN(parsed) ? undefined : parsed;
    }
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
    this.normalizeKosten();

    // Creating a new trip inline: create it first, then attach the event to it.
    if (this.form.tripChoice === CREATE_NEW_TRIP) {
      const name = this.form.newTripName.trim();
      if (!name) {
        this.errorMessage.set('Please enter a name for the new trip.');
        return;
      }
      this.isBusy.set(true);
      this.tripService.createTrip(name).subscribe({
        next: (trip) => {
          this.trips.update((list) => [...list, trip]);
          this.submitEvent(trip.id);
        },
        error: (err) => {
          this.isBusy.set(false);
          this.errorMessage.set(this.readError(err, 'Could not create the trip. Please try again.'));
        }
      });
      return;
    }

    this.isBusy.set(true);
    this.submitEvent(typeof this.form.tripChoice === 'number' ? this.form.tripChoice : null);
  }

  /** Sends the confirmed values to the backend, optionally attaching them to a trip. */
  private submitEvent(tripId: number | null): void {
    const request: GmailImportRequest = {
      title: this.form.title.trim() || 'Untitled event',
      description: this.emptyToNull(this.form.description),
      date: this.form.date,
      startTime: this.emptyToNull(this.form.startTime),
      endTime: this.emptyToNull(this.form.endTime),
      kosten: typeof this.form.kosten === 'number' ? this.form.kosten : 0,
      tripId
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
      kosten: undefined,
      tripChoice: undefined,
      newTripName: ''
    };
  }
}
