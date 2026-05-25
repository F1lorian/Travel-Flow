/* back-front relation: trip-detail page — calls GET /api/trips/{id}
   and POST /api/trips/{id}/events via TripService. Events are included
   in the trip response, so no separate events fetch is needed. */

import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TripService, Trip, Event, Category } from '../../services/trip.service';

@Component({
  selector: 'app-trip-detail',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './trip-detail.html',
  styleUrl: './trip-detail.css'
})
export class TripDetail implements OnInit {
  private tripService = inject(TripService);
  private route = inject(ActivatedRoute);

  trip = signal<Trip | null>(null);
  events = signal<Event[]>([]);
  recentCategories = signal<Category[]>([]);
  errorMessage = '';

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
    const dateTime = this.newEventDateTime + ':00';
    const category = this.newEventCategory.trim() || undefined;

    this.tripService.addEvent(tripId, this.newEventName, dateTime, this.newEventKosten, category).subscribe({
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

  formatDateTime(dt: string): string {
    return new Date(dt).toLocaleString();
  }
}