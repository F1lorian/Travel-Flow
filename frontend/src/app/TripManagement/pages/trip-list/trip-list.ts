/* back-front relation: trip-list page — calls GET /api/trips and POST /api/trips via TripService. */

import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TripService, Trip } from '../../services/trip.service';
import { NotificationService } from '../../../components/notification-menu/notification.service';
import { FeedbackService, apiErrorMessage } from '../../../services/feedback.service';

@Component({
  selector: 'app-trip-list',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './trip-list.html',
  styleUrl: './trip-list.css'
})
export class TripList implements OnInit {
  private tripService = inject(TripService);
  private notificationService = inject(NotificationService);
  private feedback = inject(FeedbackService);

  // Signal holding the loaded trips; the template re-renders when it changes.
  trips = signal<Trip[]>([]);
  newTripName = '';
  newTripStartDate = '';
  newTripEndDate = '';
  errorMessage = '';

  // Load all trips once when the page opens.
  ngOnInit() {
    this.tripService.getAllTrips().subscribe({
      next: (data) => this.trips.set(data),
      error: (err) =>
        this.errorMessage = 'Could not load trips: ' + apiErrorMessage(err, 'server not reachable')
    });
  }

  createTrip() {
    // Name is required; dates are optional (sent as undefined when blank).
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
        // Append the new trip and clear the form on success. The user gets a toast right away,
        // and the same milestone lands in the notification bell (and their email inbox).
        this.trips.update(list => [...list, trip]);
        this.feedback.success(`Trip "${trip.name}" created`);
        this.notificationService.add(`Trip "${trip.name}" created`);
        this.newTripName = '';
        this.newTripStartDate = '';
        this.newTripEndDate = '';
        this.errorMessage = '';
      },
      error: (err) => {
        // Tell the user why it failed (e.g. "Trip end date cannot be before its start date").
        const reason = apiErrorMessage(err, 'server not reachable');
        this.errorMessage = `Trip can't be created: ${reason}`;
        this.feedback.error(this.errorMessage);
      }
    });
  }
}