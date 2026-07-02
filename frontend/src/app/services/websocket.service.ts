import { Injectable } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import * as SockJS from 'sockjs-client';
import { Subject } from 'rxjs';
import { FriendRequestDTO } from './friends.service';

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private client: Client | null = null;
  private connected = false;

  newFriendRequest$ = new Subject<FriendRequestDTO>();
  friendRequestAnswered$ = new Subject<FriendRequestDTO>();

  connect(): void {
    const token = localStorage.getItem('sepToken');
    if (!token || this.connected) {
      return;
    }

    this.client = new Client({
      webSocketFactory: () => new (SockJS as any)(`/ws?token=${encodeURIComponent(token)}`),
      reconnectDelay: 5000,
      onConnect: () => {
        this.connected = true;

        this.client!.subscribe('/user/queue/friend-requests', (message: IMessage) => {
          this.newFriendRequest$.next(JSON.parse(message.body));
        });

        this.client!.subscribe('/user/queue/friend-requests/status', (message: IMessage) => {
          this.friendRequestAnswered$.next(JSON.parse(message.body));
        });
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame);
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
