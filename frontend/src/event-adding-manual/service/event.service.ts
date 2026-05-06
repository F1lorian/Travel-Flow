import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {AddEventRequest} from '../models/AddEventRequest';
import { AddEventResponse } from '../models/AddEventResponse';


@Injectable({
  providedIn: 'root',
})
export class EventService {
  private readonly http = inject(HttpClient);
  private readonly manualEventEndpoint = '/api/event/create/manual';

  createManualEvent(newEvent: AddEventRequest): Observable<AddEventResponse> {
    return this.http.post<AddEventResponse>(this.manualEventEndpoint, newEvent);
  }
}
