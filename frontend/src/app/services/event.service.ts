import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class EventService {
  private readonly http = inject(HttpClient);
  private readonly manualEventEndpoint = 'http://localhost:8082/api/event/create/manual';

}
