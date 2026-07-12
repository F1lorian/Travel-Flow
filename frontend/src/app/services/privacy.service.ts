import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Represents one privacy permission entry for a friend.
 *
 * The current user can decide whether this friend is allowed to see
 * events and/or profile information.
 */
export interface PrivacyPermission {
  friendId: number;
  username: string;
  firstName: string;
  surname: string;
  canSeeEvents: boolean;
  canSeeProfile: boolean;
}

/**
 * Request body used when updating privacy permissions for one friend.
 */
export interface UpdatePrivacyPermissionRequest {
  canSeeEvents: boolean;
  canSeeProfile: boolean;
}

/**
 * Profile information that can be shared with another user.
 *
 * Sensitive information like email, password hash or security answers
 * is intentionally not included.
 */
export interface SharedUserProfile {
  id: number;
  username: string;
  firstName: string;
  surname: string;
}

/**
 * Event information that can be shared with a friend if permission was granted.
 */
export interface SharedEvent {
  id: number;
  name: string;
  startTime: string;
  endTime: string;
  kosten: number;
  tripId: number | null;
}

/**
 * Service for all privacy-related backend calls.
 *
 * This service is the central frontend API layer for:
 * - loading privacy permissions
 * - updating privacy permissions
 * - loading shared profiles
 * - loading shared events
 */
@Injectable({
  providedIn: 'root'
})
export class PrivacyService {
  private readonly http = inject(HttpClient);

  // Base path for all privacy-related backend endpoints.
  private readonly apiUrl = '/api/privacy';

  /**
   * Loads the privacy permissions configured by the current user.
   *
   * The backend determines the current user from the JWT token.
   */
  getPermissions(): Observable<PrivacyPermission[]> {
    return this.http.get<PrivacyPermission[]>(`${this.apiUrl}/permissions`);
  }

  /**
   * Updates what one specific friend is allowed to see.
   *
   * @param friendId id of the friend whose permissions should be changed
   * @param request new permission values
   */
  updatePermission(
    friendId: number,
    request: UpdatePrivacyPermissionRequest
  ): Observable<PrivacyPermission> {
    return this.http.put<PrivacyPermission>(
      `${this.apiUrl}/permissions/${friendId}`,
      request
    );
  }

  /**
   * Loads shared profile information of another user.
   *
   * The backend only returns the profile if the current user has permission.
   */
  getSharedProfile(userId: number): Observable<SharedUserProfile> {
    return this.http.get<SharedUserProfile>(`${this.apiUrl}/users/${userId}/profile`);
  }

  /**
   * Loads shared events of another user.
   *
   * The backend only returns events if the current user has permission.
   */
  getSharedEvents(userId: number): Observable<SharedEvent[]> {
    return this.http.get<SharedEvent[]>(`${this.apiUrl}/users/${userId}/events`);
  }
}
