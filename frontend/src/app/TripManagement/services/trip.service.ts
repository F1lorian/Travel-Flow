/* back-front relation: TripService is the single point of contact between
   the Angular frontend and the Spring Boot backend API at /api/trips.
   All HTTP calls go through here — components never call the backend directly. */

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Trip {
  id: number;
  name: string;
  startDate?: string;
  endDate?: string;
  events: Event[];
}

export interface Event {
  id: number;
  name: string;
  startTime?: string;
  endTime?: string;
  kosten: number; // cost in EUR (German "Kosten"); 0 means free/no price
  categoryName?: string;
}

export interface Category {
  id: number;
  name: string;
}

export interface EventRecommendation {
  name: string;
  dateTime: string;
  price: number | null;
  description: string;
  location: string;
  sourceUrl: string;
}

@Injectable({ providedIn: 'root' })
export class TripService {
  private http = inject(HttpClient);
  private apiUrl = '/api/trips';
  private categoriesUrl = '/api/categories';

  // Each method below is a thin wrapper over one backend REST call.
  // Components subscribe to the returned Observable to get the result.

  getAllTrips(): Observable<Trip[]> {
    return this.http.get<Trip[]>(this.apiUrl);
  }

  getTrip(id: number): Observable<Trip> {
    return this.http.get<Trip>(`${this.apiUrl}/${id}`);
  }

  createTrip(name: string, startDate?: string, endDate?: string): Observable<Trip> {
    return this.http.post<Trip>(this.apiUrl, { name, startDate, endDate });
  }

  addEvent(tripId: number, name: string, startTime: string | undefined, endTime: string | undefined, kosten: number, categoryName?: string): Observable<Event> {
    return this.http.post<Event>(`${this.apiUrl}/${tripId}/events`, { name, startTime, endTime, kosten, categoryName });
  }

  getRecentCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${this.categoriesUrl}/recent`);
  }

  deleteTrip(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  deleteEvent(tripId: number, eventId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${tripId}/events/${eventId}`);
  }

  moveEvent(tripId: number, eventId: number, newTripId: number): Observable<Event> {
    return this.http.put<Event>(`${this.apiUrl}/${tripId}/events/${eventId}/move/${newTripId}`, {});
  }

  searchEventRecommendations(location: string): Observable<EventRecommendation[]> {
    return this.http.post<EventRecommendation[]>(`${this.apiUrl}/event-search`, { location });
  }
}
