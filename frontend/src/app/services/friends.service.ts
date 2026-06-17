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

@Injectable({
  providedIn: 'root'
})
export class FriendsService {
  private readonly http = inject(HttpClient);
  // Base path for friends-related backend endpoints.
  private apiUrl = '/api/friends';

getFriends(): Observable<Friend[]> {
  return this.http.get<Friend[]>(`${this.apiUrl}`);}

searchUsers(name: string): Observable<FriendSearchResult[]> {
  return this.http.get<FriendSearchResult[]>(
    `${this.apiUrl}/search?name=${encodeURIComponent(name)}`
  );
}

addFriend(userId: number): Observable<unknown> {
  return this.http.post(`${this.apiUrl}/${userId}`, {});}

removeFriend(userId: number): Observable<unknown> {
  return this.http.delete(`${this.apiUrl}/${userId}`);}

}

