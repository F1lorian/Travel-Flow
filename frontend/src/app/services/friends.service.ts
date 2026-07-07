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

@Injectable({
  providedIn: 'root'
})
export class FriendsService {
  private readonly http = inject(HttpClient);
  // Base path for friends-related backend endpoints.
  private apiUrl = '/api/friends';
  private iviteUrl = '/api/TripInvites';

getFriends(): Observable<Friend[]> {
  return this.http.get<Friend[]>(`${this.apiUrl}`);}

searchUsers(name: string): Observable<FriendSearchResult[]> {
  return this.http.get<FriendSearchResult[]>(
    `${this.apiUrl}/search?name=${encodeURIComponent(name)}`
  );
}

removeFriend(userId: number): Observable<unknown> {
  return this.http.delete(`${this.apiUrl}/${userId}`);}

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

  sendIviteMessage(tripID: number, friendID: number): Observable<unknown> {
    return this.http.post(`${this.iviteUrl}/${tripID}/${friendID}`, {});
  }
}

