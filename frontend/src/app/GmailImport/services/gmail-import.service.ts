import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreatedEvent,
  ExtractedEvent,
  GmailAuthUrl,
  GmailAutoImportResult,
  GmailConnectionStatus,
  GmailImportRequest
} from '../models/gmail-import.models';

/**
 * Talks to the backend Gmail import API. The JWT is added automatically by the global
 * auth interceptor, so this service only has to describe the endpoints.
 */
@Injectable({ providedIn: 'root' })
export class GmailImportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/gmail';

  /** Returns whether the user has connected a Gmail account. */
  getStatus(): Observable<GmailConnectionStatus> {
    return this.http.get<GmailConnectionStatus>(`${this.baseUrl}/status`);
  }

  /** Fetches the Google consent URL to start the OAuth flow. */
  getAuthUrl(): Observable<GmailAuthUrl> {
    return this.http.get<GmailAuthUrl>(`${this.baseUrl}/auth-url`);
  }

  /** Completes the OAuth flow with the authorization code returned by Google. */
  connect(code: string): Observable<GmailConnectionStatus> {
    return this.http.post<GmailConnectionStatus>(`${this.baseUrl}/connect`, { code });
  }

  /** Disconnects the linked Gmail account. */
  disconnect(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/disconnect`, {});
  }

  /** Scans recent emails and returns the events that could be extracted. */
  scan(): Observable<ExtractedEvent[]> {
    return this.http.get<ExtractedEvent[]>(`${this.baseUrl}/scan`);
  }

  /** Creates a calendar event from the confirmed values. */
  importEvent(request: GmailImportRequest): Observable<CreatedEvent> {
    return this.http.post<CreatedEvent>(`${this.baseUrl}/import`, request);
  }

  /** Scans recent emails and creates events automatically (no review), returning a summary. */
  autoImport(): Observable<GmailAutoImportResult> {
    return this.http.post<GmailAutoImportResult>(`${this.baseUrl}/import-auto`, {});
  }
}
