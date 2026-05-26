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
  dateTime: string;
  kosten: number;
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

  getAllTrips(): Observable<Trip[]> {
    return this.http.get<Trip[]>(this.apiUrl);
  }

  getTrip(id: number): Observable<Trip> {
    return this.http.get<Trip>(`${this.apiUrl}/${id}`);
  }

  createTrip(name: string): Observable<Trip> {
    return this.http.post<Trip>(this.apiUrl, { name });
  }

  addEvent(tripId: number, name: string, dateTime: string, kosten: number): Observable<Event> {
    return this.http.post<Event>(`${this.apiUrl}/${tripId}/events`, { name, dateTime, kosten });
  }

  searchEventRecommendations(location: string): Observable<EventRecommendation[]> {
    return this.http.post<EventRecommendation[]>(`${this.apiUrl}/event-search`, { location });
  }
}
