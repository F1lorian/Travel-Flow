import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TripService, Trip } from '../../services/trip.service';

@Component({
  selector: 'app-trip-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './trip-list.html',
  styleUrl: './trip-list.css'
})
export class TripList implements OnInit {
  private tripService = inject(TripService);

  trips = signal<Trip[]>([]);
  newTripName = '';
  errorMessage = '';

  ngOnInit() {
    this.loadTrips();
  }

  loadTrips() {
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

    this.tripService.createTrip(this.newTripName).subscribe({
      next: (trip) => {
        this.trips.update(list => [...list, trip]);
        this.newTripName = '';
        this.errorMessage = '';
      },
      error: (err) => this.errorMessage = 'Could not create trip: ' + err.message
    });
  }
}
