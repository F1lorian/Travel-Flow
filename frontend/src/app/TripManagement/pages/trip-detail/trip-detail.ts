/* back-front relation: trip-detail page — calls GET /api/trips/{id}
   and POST /api/trips/{id}/events via TripService. Events are included
   in the trip response, so no separate events fetch is needed. */

import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TripService, Trip, Event } from '../../services/trip.service';

@Component({
  selector: 'app-trip-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
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

  formatDateTime(dt: string): string {
    return new Date(dt).toLocaleString();
  }
}