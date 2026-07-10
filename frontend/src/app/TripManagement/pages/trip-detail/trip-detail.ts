/* back-front relation: trip-detail page — calls GET /api/trips/{id}
   and POST /api/trips/{id}/events via TripService. Events are included
   in the trip response, so no separate events fetch is needed. */

import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { EventRecommendation, TripService, Trip, Event, Category } from '../../services/trip.service';
import { EventSearchComponent } from '../../../EventRecommendation/pages/event-search/event-search';
import { EventService as CalendarEventService } from '../../../../event-adding-manual/service/event.service';
import { ConflictDialog } from '../../../components/conflict-dialog/conflict-dialog';
import { EventDialog } from '../../../components/event-dialog/event-dialog';
import { InviteDialog } from '../../../components/invite-dialog/invite-dialog';

@Component({
  selector: 'app-trip-detail',
  standalone: true,
  imports: [FormsModule, RouterLink, EventSearchComponent],
  templateUrl: './trip-detail.html',
  styleUrl: './trip-detail.css'
})
export class TripDetail implements OnInit {
  private tripService = inject(TripService);
  private calendarEventService = inject(CalendarEventService);
  private dialog = inject(MatDialog);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  trip = signal<Trip | null>(null);
  events = signal<Event[]>([]);
  recentCategories = signal<Category[]>([]);
  showDeleteConfirm = signal(false);
  errorMessage = '';

  // Delete-event modal state. The modal is a small 3-step flow driven by deleteStep:
  //   'choice'       -> delete the event, or move it to another trip?
  //   'pick-trip'    -> search and pick the destination trip
  //   'confirm-move' -> confirm moving the event to the chosen trip
  eventToDelete = signal<Event | null>(null);
  deleteStep = signal<'choice' | 'pick-trip' | 'confirm-move'>('choice');
  otherTrips = signal<Trip[]>([]);
  tripSearch = '';
  targetTrip = signal<Trip | null>(null);

  ngOnInit() {
    // Trip id comes from the route (/trips/:id). The trip response already
    // includes its events, so we seed both signals from one request.
    const tripId = Number(this.route.snapshot.paramMap.get('id'));
    this.tripService.getTrip(tripId).subscribe({
      next: (trip) => {
        this.trip.set(trip);
        this.events.set(trip.events);
      },
      error: (err) => this.errorMessage = 'Could not load trip: ' + err.message
    });

    this.tripService.getRecentCategories().subscribe({
      next: (cats) => this.recentCategories.set(cats),
      error: () => { /* non-fatal: dropdown will just be empty */ }
    });
  }

  openCreateDialog() {
    const tripId = this.trip()?.id;
    const dialogRef = this.dialog.open(EventDialog, {
      maxHeight: '90vh',
      height: 'auto',
      data: { mode: 'create', tripId: tripId }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result?.action === 'created') {
        this.events.update(list => [...list, {
          id: Number(result.event.id),
          name: result.event.name,
          kosten: result.event.kosten,
          startTime: result.event.start,
          endTime: result.event.end,
        }]);
      }
      if (result?.action === 'error') {
        this.errorMessage = result.message;
      }
    });
  }

  openInviteDialog() {
    const tripId = this.trip()?.id;
    if (!tripId) return;

    this.dialog.open(InviteDialog, {
      maxWidth: '480px',
      width: '90vw',
      panelClass: 'invite-dialog-panel',
      data: { tripId }
    });
  }
  openShowDialog(event: Event) {
    const dialogRef = this.dialog.open(EventDialog, {
      maxHeight: '90vh',
      height: 'auto',
      data: {
        mode: 'show',
        id: String(event.id),
        title: event.name,
        kosten: event.kosten,
        tripId: this.trip()?.id,
        start: event.startTime?.substring(0, 16),
        end: event.endTime?.substring(0, 16),
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result?.action === 'edit') {
        this.openEditDialog(event);
      }
    });
  }

  openEditDialog(event: Event) {
    const dialogRef = this.dialog.open(EventDialog, {
      maxHeight: '90vh',
      height: 'auto',
      data: {
        mode: 'edit',
        id: String(event.id),
        title: event.name,
        kosten: event.kosten,
        tripId: this.trip()?.id,
        start: event.startTime?.substring(0, 16),
        end: event.endTime?.substring(0, 16),
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result?.action === 'saved') {
        this.events.update(list =>
          list.map(e => e.id === event.id
            ? {
              ...e,
              name: result.event.name,
              kosten: result.event.kosten,
              startTime: result.event.start,
              endTime: result.event.end,
            }
            : e
          )
        );
      }
      if (result?.action === 'deleted') {
        this.events.update(list => list.filter(e => e.id !== event.id));
      }
      if (result?.action === 'error') {
        this.errorMessage = result.message;
      }
    });
  }

  // Called when the user clicks "Add" on an AI recommendation. Turns the
  // recommendation into a real event (normalizing its date and defaulting an
  // unknown price to 0) and saves it to the current trip.
  addRecommendedEvent(recommendation: EventRecommendation) {
    const tripId = this.trip()?.id;
    if (!tripId) return;

    const startTime = this.normalizeRecommendationDate(recommendation.dateTime);
    const cost = recommendation.price ?? 0;

    this.checkConflictsBeforeCreate(startTime, undefined, () => {
      this.tripService.addEvent(tripId, recommendation.name, startTime, undefined, cost).subscribe({
        next: (event) => {
          this.events.update(list => [...list, event]);
          this.errorMessage = '';
        },
        error: (err) => this.errorMessage = 'Could not add recommended event: ' + err.message
      });
    });
  }

  formatDateTime(dt?: string): string {
    return dt ? new Date(dt).toLocaleString() : '';
  }

  openDeleteConfirm() {
    this.showDeleteConfirm.set(true);
  }

  cancelDelete() {
    this.showDeleteConfirm.set(false);
  }

  confirmDelete() {
    const tripId = this.trip()?.id;
    if (!tripId) return;
    this.tripService.deleteTrip(tripId).subscribe({
      next: () => {
        this.showDeleteConfirm.set(false);
        this.router.navigate(['/trips']);
      },
      error: (err) => this.errorMessage = 'Could not delete trip: ' + err.message
    });
  }

  openEventDelete(event: Event) {
    this.eventToDelete.set(event);
    this.deleteStep.set('choice');
    this.tripSearch = '';
    this.targetTrip.set(null);
  }

  closeEventModal() {
    this.eventToDelete.set(null);
    this.targetTrip.set(null);
    this.tripSearch = '';
  }

  confirmDeleteEvent() {
    const tripId = this.trip()?.id;
    const event = this.eventToDelete();
    if (!tripId || !event) return;
    this.tripService.deleteEvent(tripId, event.id).subscribe({
      next: () => {
        this.events.update(list => list.filter(e => e.id !== event.id));
        this.closeEventModal();
      },
      error: (err) => this.errorMessage = 'Could not delete event: ' + err.message
    });
  }

  // Move-flow step 1: load every other trip (excluding the current one) as
  // possible destinations, then advance the modal to the trip picker.
  startMoveFlow() {
    const currentTripId = this.trip()?.id;
    if (!currentTripId) return;
    this.tripService.getAllTrips().subscribe({
      next: (trips) => {
        this.otherTrips.set(trips.filter(t => t.id !== currentTripId));
        this.deleteStep.set('pick-trip');
      },
      error: (err) => this.errorMessage = 'Could not load trips: ' + err.message
    });
  }

  // Live filter for the trip picker's search box.
  filteredOtherTrips(): Trip[] {
    const q = this.tripSearch.trim().toLowerCase();
    if (!q) return this.otherTrips();
    return this.otherTrips().filter(t => t.name.toLowerCase().includes(q));
  }

  selectTargetTrip(trip: Trip) {
    this.targetTrip.set(trip);
    this.deleteStep.set('confirm-move');
  }

  confirmMove() {
    const tripId = this.trip()?.id;
    const event = this.eventToDelete();
    const target = this.targetTrip();
    if (!tripId || !event || !target) return;
    this.tripService.moveEvent(tripId, event.id, target.id).subscribe({
      next: () => {
        this.events.update(list => list.filter(e => e.id !== event.id));
        this.closeEventModal();
      },
      error: (err) => this.errorMessage = 'Could not move event: ' + err.message
    });
  }

  // Coerces a recommendation's date string into the backend's expected
  // "YYYY-MM-DDTHH:MM:SS" form. Missing or unparseable dates fall back to now.
  private normalizeRecommendationDate(dateTime: string): string {
    if (!dateTime) {
      return new Date().toISOString().slice(0, 19);
    }

    // A 16-char value ("YYYY-MM-DDTHH:MM") has no seconds — append ":00".
    const normalized = dateTime.length === 16 ? `${dateTime}:00` : dateTime;
    const parsedDate = new Date(normalized);
    if (Number.isNaN(parsedDate.getTime())) {
      return new Date().toISOString().slice(0, 19);
    }

    return normalized.slice(0, 19);
  }

  private checkConflictsBeforeCreate(startTime: string | undefined, endTime: string | undefined, createEvent: () => void) {
    if (!startTime) {
      createEvent();
      return;
    }

    this.calendarEventService.getConflictEvents(startTime, endTime ?? startTime).subscribe({
      next: conflicts => {
        if (conflicts.length === 0) {
          createEvent();
          return;
        }

        const dialogRef = this.dialog.open(ConflictDialog, {
          maxWidth: '560px',
          data: { conflicts },
        });

        dialogRef.afterClosed().subscribe(confirmed => {
          if (confirmed) {
            createEvent();
          }
        });
      },
      error: (err) => this.errorMessage = err?.error || 'Konfliktpruefung fehlgeschlagen.'
    });
  }
}
