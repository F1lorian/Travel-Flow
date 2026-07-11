import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

export interface Friend {
  id: number;
  username: string;
  firstName: string;
  surname: string;
}
export interface FriendSearchResult extends Friend {
  connected: boolean;
}

export type FriendRequestStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED';

export interface FriendRequestDTO {
  id: number;
  senderId: number;
  senderUsername: string;
  receiverId: number;
  receiverUsername: string;
  status: FriendRequestStatus;
}

export interface TripInviteDTO {
  tripId: number;
  tripName: string;
  invitedByUserId: number;
  invitedByUsername: string;
  status: FriendRequestStatus;
}

export interface EventInviteDTO {
  eventId: number;
  eventName: string;
  invitedByUserId: number;
  invitedByUsername: string;
  status: FriendRequestStatus;
}

@Injectable({
  providedIn: 'root'
})
export class FriendsService {
  private readonly http = inject(HttpClient);
  // Base path for friends-related backend endpoints.
  private apiUrl = '/api/friends';
  private tripInviteUrl = '/api/TripInvites';
  private eventUrl = '/api/event';

getFriends(): Observable<Friend[]> {
  return this.http.get<Friend[]>(`${this.apiUrl}`);}

searchUsers(name: string): Observable<FriendSearchResult[]> {
  return this.http.get<FriendSearchResult[]>(
    `${this.apiUrl}/search?name=${encodeURIComponent(name)}`
  );
}

removeFriend(userId: number): Observable<unknown> {
  return this.http.delete(`${this.apiUrl}/${userId}`);}

  //friend requests

  sendFriendRequest(userId: number): Observable<FriendRequestDTO> {
    return this.http.post<FriendRequestDTO>(`${this.apiUrl}/requests/${userId}`, {});
  }

  acceptFriendRequest(requestId: number): Observable<FriendRequestDTO> {
    return this.http.post<FriendRequestDTO>(`${this.apiUrl}/requests/${requestId}/accept`, {});
  }

  declineFriendRequest(requestId: number): Observable<FriendRequestDTO> {
    return this.http.post<FriendRequestDTO>(`${this.apiUrl}/requests/${requestId}/decline`, {});
  }

  getIncomingRequests(): Observable<FriendRequestDTO[]> {
    return this.http.get<FriendRequestDTO[]>(`${this.apiUrl}/requests/incoming`);
  }

  //trip invites

  sendInviteMessage(tripID: number, friendID: number): Observable<unknown> {
    return this.http.post(`${this.tripInviteUrl}/${tripID}/${friendID}`, {});
  }

  acceptTripInvite(tripId: number): Observable<unknown> {
    return this.http.post(`${this.tripInviteUrl}/${tripId}/accept`, {});
  }

  declineTripInvite(tripId: number): Observable<unknown> {
    return this.http.post(`${this.tripInviteUrl}/${tripId}/decline`, {});
  }

  getIncomingTripInvites(): Observable<TripInviteDTO[]> {
    return this.http.get<TripInviteDTO[]>(`${this.tripInviteUrl}/incoming`);
  }

  //event invites

  getIncomingEventInvites(): Observable<EventInviteDTO[]> {
    return this.http.get<EventInviteDTO[]>(`${this.eventUrl}/invites/incoming`);
  }

  acceptEventInvite(eventId: number): Observable<unknown> {
    return this.http.put(`${this.eventUrl}/${eventId}/respond?status=ACCEPTED`, {});
  }

  declineEventInvite(eventId: number): Observable<unknown> {
    return this.http.put(`${this.eventUrl}/${eventId}/respond?status=DECLINED`, {});
  }
}

