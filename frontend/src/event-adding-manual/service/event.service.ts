import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {AddEventRequest} from '../models/AddEventRequest';


@Injectable({
  providedIn: 'root',
})
export class EventService {
  private readonly http = inject(HttpClient);
  private readonly manualEventEndpoint = 'http://localhost:8082/api/event/create/manual';

  createManualEvent(newEvent: AddEventRequest): Observable<unknown> {
    return this.http.post(this.manualEventEndpoint, newEvent);
  }
}
