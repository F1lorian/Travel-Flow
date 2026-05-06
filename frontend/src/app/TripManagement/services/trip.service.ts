import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

// TypeScript versions of your backend models
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

@Injectable({ providedIn: 'root' })
export class TripService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8080/api/trips';

  // GET /api/trips
  getAllTrips(): Observable<Trip[]> {
    return this.http.get<Trip[]>(this.apiUrl);
  }

  // GET /api/trips/{id}
  getTrip(id: number): Observable<Trip> {
    return this.http.get<Trip>(`${this.apiUrl}/${id}`);
  }

  // POST /api/trips
  createTrip(name: string): Observable<Trip> {
    return this.http.post<Trip>(this.apiUrl, { name });
  }

  // GET /api/trips/{tripId}/events
  getEventsForTrip(tripId: number): Observable<Event[]> {
    return this.http.get<Event[]>(`${this.apiUrl}/${tripId}/events`);
  }

  // POST /api/trips/{tripId}/events
  addEvent(tripId: number, name: string, dateTime: string, kosten: number): Observable<Event> {
    return this.http.post<Event>(`${this.apiUrl}/${tripId}/events`, { name, dateTime, kosten });
  }
}
