 /* back-front relation: trip-detail page — calls GET /api/trips/{id}
   and POST /api/trips/{id}/events via TripService. Events are included
   in the trip response, so no separate events fetch is needed. */

import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EventRecommendation, TripService, Trip, Event, Category } from '../../services/trip.service';
import { EventSearchComponent } from '../../../EventRecommendation/pages/event-search/event-search';

@Component({
  selector: 'app-trip-detail',
  standalone: true,
  imports: [FormsModule, RouterLink, EventSearchComponent],
  templateUrl: './trip-detail.html',
  styleUrl: './trip-detail.css'
})
export class TripDetail implements OnInit {
  private tripService = inject(TripService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  trip = signal<Trip | null>(null);
  events = signal<Event[]>([]);
  recentCategories = signal<Category[]>([]);
  showDeleteConfirm = signal(false);
  errorMessage = '';

  // Delete-event modal state
  eventToDelete = signal<Event | null>(null);
  deleteStep = signal<'choice' | 'pick-trip' | 'confirm-move'>('choice');
  otherTrips = signal<Trip[]>([]);
  tripSearch = '';
  targetTrip = signal<Trip | null>(null);

  newEventName = '';
  newEventDateTime = '';
  newEventKosten = 0;
  newEventCategory = '';

  ngOnInit() {
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

  addEvent() {
    const tripId = this.trip()?.id;
    if (!tripId) return;

    if (!this.newEventName.trim()) {
      this.errorMessage = 'Please enter an event name';
      return;
    }
    if (!this.newEventDateTime) {
      this.errorMessage = 'Please pick a date and time';
      return;
    }

    // datetime-local gives YYYY-MM-DDTHH:MM, backend needs seconds appended
    const startTime = this.newEventDateTime + ':00';
    const category = this.newEventCategory.trim() || undefined;

    this.tripService.addEvent(tripId, this.newEventName, startTime, this.newEventKosten, category).subscribe({
      next: (event) => {
        this.events.update(list => [...list, event]);
        this.newEventName = '';
        this.newEventDateTime = '';
        this.newEventKosten = 0;
        this.newEventCategory = '';
        this.errorMessage = '';

        // If user typed a brand-new category, refresh the suggestion list
        if (event.categoryName && !this.recentCategories().some(c => c.name.toLowerCase() === event.categoryName!.toLowerCase())) {
          this.tripService.getRecentCategories().subscribe(cats => this.recentCategories.set(cats));
        }
      },
      error: (err) => this.errorMessage = 'Could not add event: ' + err.message
    });
  }

  addRecommendedEvent(recommendation: EventRecommendation) {
    const tripId = this.trip()?.id;
    if (!tripId) return;

    const startTime = this.normalizeRecommendationDate(recommendation.dateTime);
    const cost = recommendation.price ?? 0;

    this.tripService.addEvent(tripId, recommendation.name, startTime, cost).subscribe({
      next: (event) => {
        this.events.update(list => [...list, event]);
        this.errorMessage = '';
      },
      error: (err) => this.errorMessage = 'Could not add recommended event: ' + err.message
    });
  }

  formatDateTime(dt: string): string {
    return new Date(dt).toLocaleString();
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

  private normalizeRecommendationDate(dateTime: string): string {
    if (!dateTime) {
      return new Date().toISOString().slice(0, 19);
    }

    const normalized = dateTime.length === 16 ? `${dateTime}:00` : dateTime;
    const parsedDate = new Date(normalized);
    if (Number.isNaN(parsedDate.getTime())) {
      return new Date().toISOString().slice(0, 19);
    }

    return normalized.slice(0, 19);
  }
}
