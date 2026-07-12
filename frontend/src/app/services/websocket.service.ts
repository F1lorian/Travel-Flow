import { Injectable } from '@angular/core';
import { Client, IFrame, IMessage } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import { EventInviteDTO, FriendRequestDTO, TripInviteDTO } from './friends.service';

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private client: Client | null = null;
  private connected = false;

  newFriendRequest$ = new Subject<FriendRequestDTO>();
  friendRequestAnswered$ = new Subject<FriendRequestDTO>();
  friendRemoved$ = new Subject<number>();

  newTripInvite$ = new Subject<TripInviteDTO>();
  tripInviteAnswered$ = new Subject<TripInviteDTO>();

  newEventInvite$ = new Subject<EventInviteDTO>();
  eventInviteAnswered$ = new Subject<EventInviteDTO>();

  /**
   * Creates a WebSocket connection for real-time updates.
   *
   * The JWT token is passed as a query parameter so the backend can identify the user.
   */
  connect(): void {
    const token = localStorage.getItem('sepToken');

    if (!token || this.connected) {
      return;
    }

    this.client = new Client({
      brokerURL: `ws://localhost:8082/ws?token=${encodeURIComponent(token)}`,
      reconnectDelay: 5000,

      onConnect: () => {
        this.connected = true;

        this.client?.subscribe('/user/queue/friend-requests', (message: IMessage) => {
          this.newFriendRequest$.next(JSON.parse(message.body));
        });

        this.client?.subscribe('/user/queue/friend-requests/status', (message: IMessage) => {
          this.friendRequestAnswered$.next(JSON.parse(message.body));
        });

        this.client?.subscribe('/user/queue/friend-removed', (message: IMessage) => {
          this.friendRemoved$.next(JSON.parse(message.body));
        });

        this.client?.subscribe('/user/queue/trip-invites', (message: IMessage) => {
          this.newTripInvite$.next(JSON.parse(message.body));
        });

        this.client?.subscribe('/user/queue/trip-invites/status', (message: IMessage) => {
          this.tripInviteAnswered$.next(JSON.parse(message.body));
        });

        this.client?.subscribe('/user/queue/event-invites', (message: IMessage) => {
          this.newEventInvite$.next(JSON.parse(message.body));
        });

        this.client?.subscribe('/user/queue/event-invites/status', (message: IMessage) => {
          this.eventInviteAnswered$.next(JSON.parse(message.body));
        });
      },

      onStompError: (frame: IFrame) => {
        console.error('STOMP broker error:', frame.headers['message'], frame.body);
      },

      onWebSocketClose: () => {
        this.connected = false;
      },
    });

    this.client.activate();
  }

  disconnect(): void {
    this.client?.deactivate();
    this.connected = false;
  }
}
