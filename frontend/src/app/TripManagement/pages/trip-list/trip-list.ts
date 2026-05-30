/* back-front relation: trip-list page — calls GET /api/trips and POST /api/trips via TripService. */

import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TripService, Trip } from '../../services/trip.service';

@Component({
  selector: 'app-trip-list',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './trip-list.html',
  styleUrl: './trip-list.css'
})
export class TripList implements OnInit {
  private tripService = inject(TripService);

  trips = signal<Trip[]>([]);
  newTripName = '';
  newTripStartDate = '';
  newTripEndDate = '';
  errorMessage = '';

  ngOnInit() {
    this.tripService.getAllTrips().subscribe({
      next: (data) => this.trips.set(data),
      error: (err) => this.errorMessage = 'Could not load trips: ' + err.message
    });
  }

  createTrip() {
    if (!this.newTripName.trim()) {
      this.errorMessage = 'Please enter a trip name';
      return;
    }
    this.tripService.createTrip(
      this.newTripName,
      this.newTripStartDate || undefined,
      this.newTripEndDate || undefined
    ).subscribe({
      next: (trip) => {
        this.trips.update(list => [...list, trip]);
        this.newTripName = '';
        this.newTripStartDate = '';
        this.newTripEndDate = '';
        this.errorMessage = '';
      },
      error: (err) => this.errorMessage = 'Could not create trip: ' + err.message
    });
  }
}