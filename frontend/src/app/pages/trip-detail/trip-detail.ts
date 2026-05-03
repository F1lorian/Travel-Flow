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

  // Form fields
  newEventName = '';
  newEventDateTime = '';
  newEventKosten = 0;

  ngOnInit() {
    // Read the :id from the URL
    const tripId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadTrip(tripId);
    this.loadEvents(tripId);
  }

  loadTrip(id: number) {
    this.tripService.getTrip(id).subscribe({
      next: (data) => this.trip.set(data),
      error: (err) => this.errorMessage = 'Could not load trip: ' + err.message
    });
  }

  loadEvents(id: number) {
    this.tripService.getEventsForTrip(id).subscribe({
      next: (data) => this.events.set(data),
      error: (err) => this.errorMessage = 'Could not load events: ' + err.message
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

    // Convert datetime-local format (YYYY-MM-DDTHH:MM) → backend format (YYYY-MM-DDTHH:MM:SS)
    const dateTimeWithSeconds = this.newEventDateTime + ':00';

    this.tripService.addEvent(tripId, this.newEventName, dateTimeWithSeconds, this.newEventKosten).subscribe({
      next: (event) => {
        this.events.update(list => [...list, event]);
        // Reset form
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
