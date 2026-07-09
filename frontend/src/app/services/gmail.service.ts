import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ParsedEvent } from '../../event-adding-manual/models/ParsedEvent';

/** Connection status of the current user's Gmail import. */
export interface GmailConnection {
  connected: boolean;
  email: string | null;
}

/** Lightweight preview of a Gmail message shown in the import picker. */
export interface GmailMessagePreview {
  id: string;
  subject: string;
  from: string;
  date: string;
  snippet: string;
}

// Google OAuth Web client id, supplied at runtime via public/config.js
// (regenerated from the GOOGLE_OAUTH_CLIENT_ID env var in Docker, see .env).
const GOOGLE_CLIENT_ID =
  (globalThis as { __APP_CONFIG__?: { googleClientId?: string } }).__APP_CONFIG__?.googleClientId ??
  '';

const GMAIL_READONLY_SCOPE = 'https://www.googleapis.com/auth/gmail.readonly';

/**
 * Talks to the backend Gmail import endpoints and builds the Google consent URL for the
 * authorization-code flow. The auth interceptor attaches the bearer token to the /api calls.
 */
@Injectable({ providedIn: 'root' })
export class GmailService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/gmail';

  getStatus(): Observable<GmailConnection> {
    return this.http.get<GmailConnection>(`${this.apiUrl}/status`);
  }

  connect(code: string): Observable<GmailConnection> {
    return this.http.post<GmailConnection>(`${this.apiUrl}/connect`, { code });
  }

  getMessages(): Observable<GmailMessagePreview[]> {
    return this.http.get<GmailMessagePreview[]>(`${this.apiUrl}/messages`);
  }

  parseMessage(messageId: string): Observable<ParsedEvent> {
    return this.http.post<ParsedEvent>(`${this.apiUrl}/import/parse`, { messageId });
  }

  /**
   * Builds the Google consent URL for read-only Gmail access. `state` is echoed back to the
   * redirect and must be verified there to guard against CSRF. The redirect URI is derived from the
   * current origin and must match the backend's configured google.oauth.redirect-uri.
   */
  buildConsentUrl(state: string): string {
    const params = new URLSearchParams({
      client_id: GOOGLE_CLIENT_ID,
      redirect_uri: `${window.location.origin}/gmail-import`,
      response_type: 'code',
      scope: GMAIL_READONLY_SCOPE,
      access_type: 'offline',
      prompt: 'consent',
      include_granted_scopes: 'true',
      state,
    });
    return `https://accounts.google.com/o/oauth2/v2/auth?${params.toString()}`;
  }
}
