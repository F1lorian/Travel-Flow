/* back-front relation: TripService is the single point of contact between
   the Angular frontend and the Spring Boot backend API at /api/trips.
   All HTTP calls go through here — components never call the backend directly. */

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Trip {
  id: number;
  name: string;
  events: Event[];
}

export interface Event {
  id: number;
  name: string;
  startTime: string;
  endTime?: string;
  kosten: number;
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

  getAllTrips(): Observable<Trip[]> {
    return this.http.get<Trip[]>(this.apiUrl);
  }

  getTrip(id: number): Observable<Trip> {
    return this.http.get<Trip>(`${this.apiUrl}/${id}`);
  }

  createTrip(name: string): Observable<Trip> {
    return this.http.post<Trip>(this.apiUrl, { name });
  }

  addEvent(tripId: number, name: string, startTime: string, kosten: number, categoryName?: string): Observable<Event> {
    return this.http.post<Event>(`${this.apiUrl}/${tripId}/events`, { name, startTime, kosten, categoryName });
  }

  getRecentCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${this.categoriesUrl}/recent`);
  }

  createCategory(name: string): Observable<Category> {
    return this.http.post<Category>(this.categoriesUrl, { name });
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
