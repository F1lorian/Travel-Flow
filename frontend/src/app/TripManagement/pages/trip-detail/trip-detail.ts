/* back-front relation: trip-detail page — calls GET /api/trips/{id}
   and POST /api/trips/{id}/events via TripService. Events are included
   in the trip response, so no separate events fetch is needed. */

import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EventRecommendation, TripService, Trip, Event } from '../../services/trip.service';
import { EventSearchComponent } from '../event-search/event-search';

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

  trip = signal<Trip | null>(null);
  events = signal<Event[]>([]);
  errorMessage = '';

  newEventName = '';
  newEventDateTime = '';
  newEventKosten = 0;

  ngOnInit() {
    const tripId = Number(this.route.snapshot.paramMap.get('id'));
    this.tripService.getTrip(tripId).subscribe({
      next: (trip) => {
        this.trip.set(trip);
        this.events.set(trip.events);
      },
      error: (err) => this.errorMessage = 'Could not load trip: ' + err.message
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
    const dateTime = this.newEventDateTime + ':00';

    this.tripService.addEvent(tripId, this.newEventName, dateTime, this.newEventKosten).subscribe({
      next: (event) => {
        this.events.update(list => [...list, event]);
        this.newEventName = '';
        this.newEventDateTime = '';
        this.newEventKosten = 0;
        this.errorMessage = '';
      },
      error: (err) => this.errorMessage = 'Could not add event: ' + err.message
    });
  }

  addRecommendedEvent(recommendation: EventRecommendation) {
    const tripId = this.trip()?.id;
    if (!tripId) return;

    const dateTime = this.normalizeRecommendationDate(recommendation.dateTime);
    const cost = recommendation.price ?? 0;

    this.tripService.addEvent(tripId, recommendation.name, dateTime, cost).subscribe({
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
