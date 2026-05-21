import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {AddEventRequest} from '../models/AddEventRequest';
import { AddEventResponse } from '../models/AddEventResponse';
import {CalendarEventImport} from '../models/CalendarEventImport';


@Injectable({
  providedIn: 'root',
})
export class EventService {
  private readonly http = inject(HttpClient);
  private readonly manualEventEndpoint = '/api/event/create/manual';
  private readonly pullEvents = '/api/event/pull/all_events';

  createManualEvent(newEvent: AddEventRequest): Observable<AddEventResponse> {
    return this.http.post<AddEventResponse>(this.manualEventEndpoint, newEvent);
  }

  getEvents(): Observable<CalendarEventImport[]> {
    return this.http.get<CalendarEventImport[]>(this.pullEvents);
  }
}
