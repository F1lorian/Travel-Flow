import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {AddEventRequest} from '../models/AddEventRequest';
import { AddEventResponse } from '../models/AddEventResponse';
import {CalendarEventImport} from '../models/CalendarEventImport';
import {EditEventRequest} from '../models/EditEventRequest';
import {EditEventResponse} from '../models/EditEventResponse';



@Injectable({
  providedIn: 'root',
})
export class EventService {
  private readonly http = inject(HttpClient);
  private readonly manualEventEndpoint = '/api/event/create/manual';
  private readonly pullEvents = '/api/event/pull/all_events';
  private readonly editEvents = '/api/event/edit';
  private readonly deleteEvents = '/api/event/delete';

  /**
   * sends the newly created event to the backend and returns the saved event
   * @param newEvent is the created event
   */
  createManualEvent(newEvent: AddEventRequest): Observable<AddEventResponse> {
    return this.http.post<AddEventResponse>(this.manualEventEndpoint, newEvent);
  }

  /**
   * gets all the events from the database and returns them
   */
  getEvents(): Observable<CalendarEventImport[]> {
    return this.http.get<CalendarEventImport[]>(this.pullEvents);
  }

  /**
   * sends the event with its new information to the backend and returns the saved event
   * @param updatedEvent is the event with the new information
   */
  editEvent(updatedEvent: EditEventRequest):Observable<EditEventResponse>{
    return this.http.post<EditEventResponse>(`${this.editEvents}/${updatedEvent.id}`, updatedEvent);
  }

  /**
   * sends the id of the deleted event to the backend and returns the id
   * @param id
   */
  deleteEvent(id: string):Observable<string> {
    return this.http.post<string>(this.deleteEvents, Number(id));
  }

  /**
   * sends the imported .eml file to the backend and returns the created and saved event
   * @param formData is the .eml file
   * @param tripId is the optional tripId
   */
  importEvents(formData: FormData, tripId?: number): Observable<AddEventResponse> {
    let params = {};
    if (tripId !== undefined) {
      params = {tripId: tripId.toString()};
    }
    return this.http.post<AddEventResponse>('/api/event/create/import', formData, { params });
  }

  /**
   * returns the events from the database as a .ics file
   */
  exportEvents():Observable<Blob> {
    return this.http.get('/api/data/ical', {
      responseType: 'blob'
    });
  }

  getConflictEvents(start: string, end: string): Observable<AddEventResponse[]> {
    return this.http.get<AddEventResponse[]>('/api/event/conflict', {
      params: { start, end }
    });
  }


}
