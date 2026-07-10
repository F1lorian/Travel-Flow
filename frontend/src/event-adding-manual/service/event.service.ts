import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {AddEventRequest} from '../models/AddEventRequest';
import { AddEventResponse } from '../models/AddEventResponse';
import {CalendarEventImport} from '../models/CalendarEventImport';
import {EditEventRequest} from '../models/EditEventRequest';
import {EditEventResponse} from '../models/EditEventResponse';
import { ParsedEvent } from '../models/ParsedEvent';
import { UserEventDTO } from '../models/UserEventDTO';

export interface ImapMailboxRequest {
  host: string;
  port?: number;
  username: string;
  password: string;
  folder?: string;
  limit?: number;
}

export interface ImapMailSelectionRequest extends ImapMailboxRequest {
  uid: number;
}

export interface ImapMailPreview {
  uid: number;
  subject: string;
  from: string;
  sentDate: string | null;
  receivedDate: string | null;
  snippet: string;
}

@Injectable({
  providedIn: 'root',
})
export class EventService {
  private readonly http = inject(HttpClient);
  private readonly manualEventEndpoint = '/api/event/create/manual';
  private readonly pullEvents = '/api/event/pull/all_events';
  private readonly editEvents = '/api/event/edit';
  private readonly deleteEvents = '/api/event/delete';
  private readonly inviteFriendEndpoint = '/api/event';

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
   * Sends an imported .eml file to the backend for parsing only.
   * The returned event is not saved yet and has no database id.
   * @param formData is the .eml file
   * @param tripId is the optional tripId
   */
  parseEvent(formData: FormData, tripId?: number): Observable<ParsedEvent> {
    let params = {};
    if (tripId !== undefined) {
      params = {tripId: tripId.toString()};
    }
    return this.http.post<ParsedEvent>('/api/event/import/parse', formData, { params });
  }

  getImapMessages(request: ImapMailboxRequest): Observable<ImapMailPreview[]> {
    return this.http.post<ImapMailPreview[]>('/api/event/import/imap/messages', request);
  }

  parseImapMessage(request: ImapMailSelectionRequest): Observable<ParsedEvent> {
    return this.http.post<ParsedEvent>('/api/event/import/imap/parse', request);
  }

  /**
   * Saves an event that was already parsed from an imported .eml file.
   * @param parsedEvent event values extracted from the uploaded file
   * @param tripId optional trip that should be linked to the saved event
   */
  saveParsedEvent(parsedEvent: ParsedEvent, tripId?: number): Observable<AddEventResponse> {
    let params = {};
    if (tripId !== undefined) {
      params = { tripId: tripId.toString() };
    }

    return this.http.post<AddEventResponse>('/api/event/create/import', parsedEvent, { params });
  }

  /**
   * returns the events from the database as a .ics file
   */
  exportEvents():Observable<Blob> {
    return this.http.get('/api/data/ical', {
      responseType: 'blob'
    });
  }

  /**
   * Returns events that overlap the given time interval.
   * @param start start date-time of the event that should be checked
   * @param end end date-time of the event that should be checked
   */
  getConflictEvents(start: string, end: string): Observable<AddEventResponse[]> {
    return this.http.get<AddEventResponse[]>('/api/event/conflict', {
      params: { start, end }
    });
  }

  /**
   * invites a friend to an event
   * @param eventId
   * @param friendUserId
   */
  inviteFriendToEvent(eventId: string, friendUserId: number): Observable<void> {
    return this.http.post<void>(`${this.inviteFriendEndpoint}/${eventId}/invite/${friendUserId}`, {});
  }

  /**
   * sends the response to an invitation
   * @param eventId
   * @param status can be PENDING, ACCEPTED or DECLINED
   */
  respondToInvite(eventId: string, status: 'ACCEPTED' | 'DECLINED'): Observable<void> {
    return this.http.put<void>(`${this.inviteFriendEndpoint}/${eventId}/respond`, {}, {
      params: { status }
    });
  }

  /**
   * returns a list of all the invites sent by a user for a single event
   * @param eventId
   */
  getInvitesSentByUser(eventId: string): Observable<UserEventDTO[]> {
    return this.http.get<UserEventDTO[]>(`${this.inviteFriendEndpoint}/${eventId}/invites`);
  }

  getParticipants(eventId: string): Observable<UserEventDTO[]> {
    return this.http.get<UserEventDTO[]>(`${this.inviteFriendEndpoint}/${eventId}/participants`);
  }


}
