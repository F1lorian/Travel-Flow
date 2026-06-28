import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Read-only privacy details of the logged-in user, as returned by {@code GET /api/profile}. */
export interface ProfileDetails {
  username: string;
  firstName: string;
  surname: string;
  email: string;
  provider: string;
  emailVerified: boolean;
}

/** Generic single-message response used by the write endpoints. */
export interface MessageResponse {
  message: string;
}

/**
 * Body for a password change. The change must be authorised with exactly one of `code` (emailed
 * one-time code) or `googleIdToken` (a Google sign-in for the linked account).
 */
export interface ChangePasswordPayload {
  newPassword: string;
  repeatNewPassword: string;
  code?: string;
  googleIdToken?: string;
}

/**
 * Body for an account deletion. As with a password change, it must carry exactly one of `code` or
 * `googleIdToken` to prove ownership.
 */
export interface DeleteAccountPayload {
  code?: string;
  googleIdToken?: string;
}

/**
 * Client for the self-service profile endpoints under {@code /api/profile}. Every call is sent with
 * the logged-in user's JWT by the auth interceptor, so the backend resolves the account from the
 * token rather than from any request parameter.
 */
@Injectable({ providedIn: 'root' })
export class ProfileManagementService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/profile';

  /** Loads the current user's privacy details. */
  getProfile(): Observable<ProfileDetails> {
    return this.http.get<ProfileDetails>(this.apiUrl);
  }

  /** Emails the current user a one-time code, used to authorise a password change or deletion. */
  sendChangeCode(): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/send-change-code`, {});
  }

  /** Sets a new password once ownership is proven by code or Google sign-in. */
  changePassword(payload: ChangePasswordPayload): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/change-password`, payload);
  }

  /** Permanently deletes the current user's account once ownership is proven by code or Google. */
  deleteAccount(payload: DeleteAccountPayload): Observable<MessageResponse> {
    return this.http.delete<MessageResponse>(this.apiUrl, { body: payload });
  }
}
