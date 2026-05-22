import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {AddEventRequest} from '../models/AddEventRequest';
import { AddEventResponse } from '../models/AddEventResponse';
import {CalendarEventImport} from '../models/CalendarEventImport';
import {EditEventRequest} from '../models/EditEventRequest';


@Injectable({
  providedIn: 'root',
})
export class EventService {
  private readonly http = inject(HttpClient);
  private readonly manualEventEndpoint = '/api/event/create/manual';
  private readonly pullEvents = '/api/event/pull/all_events';
  private readonly editEvents = '/api/event/edit';
  private readonly deleteEvents = '/api/event/delete';


  createManualEvent(newEvent: AddEventRequest): Observable<AddEventResponse> {
    return this.http.post<AddEventResponse>(this.manualEventEndpoint, newEvent);
  }

  getEvents(): Observable<CalendarEventImport[]> {
    return this.http.get<CalendarEventImport[]>(this.pullEvents);
  }

  editEvent(updatedEvent: EditEventRequest){
    return this.http.post<EditEventRequest>(this.editEvents, updatedEvent);
  }

  deleteEvent(id: number){
    return this.http.post<number>(this.deleteEvents, id);
  }

}
