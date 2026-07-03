import {Component, inject, OnInit, OnDestroy} from '@angular/core';
import {Friend, FriendSearchResult, FriendsService, FriendRequestDTO} from '../../services/friends.service';
import {FormsModule} from '@angular/forms';
import {WebSocketService} from '../../services/websocket.service';
import {Subscription} from 'rxjs';

@Component({
  selector: 'app-friends',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './friends.html',
  styleUrls: ['./friends.css'],
})

export class Friends implements OnInit, OnDestroy {
  private readonly friendsService = inject(FriendsService);
  private readonly webSocketService = inject(WebSocketService);

  friends: Friend[] = [];
  searchResults: FriendSearchResult[] = [];
  incomingRequests: FriendRequestDTO[] = [];
  searchName = '';
  hasSearched = false;

  private subscriptions = new Subscription();

  ngOnInit() {
    this.loadFriends();
    this.loadIncomingRequests();

    // Live-Updates über WebSocket
    this.subscriptions.add(
      this.webSocketService.newFriendRequest$.subscribe((request) => {
        this.incomingRequests = [...this.incomingRequests, request];
      })
    );

    this.subscriptions.add(
      this.webSocketService.friendRequestAnswered$.subscribe((request) => {
        // eigene ausgehende Anfrage wurde beantwortet -> Freundesliste neu laden
        if (request.status === 'ACCEPTED') {
          this.loadFriends();
        }
        // aus eigener incoming-Liste entfernen, falls betroffen (z. B. bei Multi-Device)
        this.incomingRequests = this.incomingRequests.filter((r) => r.id !== request.id);
      })
    );

    this.subscriptions.add(
      this.webSocketService.friendRemoved$.subscribe((removedByUserId) => {
        this.friends = this.friends.filter((f) => f.id !== removedByUserId);
        // falls der entfernte User gerade in den Suchergebnissen sichtbar ist, connected zurücksetzen
        this.searchResults = this.searchResults.map((user) =>
          user.id === removedByUserId ? { ...user, connected: false } : user
        );
      })
    );
  }

  ngOnDestroy() {
    this.subscriptions.unsubscribe();
  }

  private loadFriends(): void {
    this.friendsService.getFriends().subscribe({
      next: (data) => (this.friends = data),
      error: (error) => console.error('Error while loading friends:', error),
    });
  }

  private loadIncomingRequests(): void {
    this.friendsService.getIncomingRequests().subscribe({
      next: (data) => (this.incomingRequests = data),
      error: (error) => console.error('Error while loading incoming requests:', error),
    });
  }

  searchUsers(){
    const name = this.searchName.trim();
    if (!name) {
      this.searchResults = [];
      this.hasSearched = false;
      return;
    }

    this.hasSearched = true;
    this.friendsService.searchUsers(name).subscribe({
      next: (data) => (this.searchResults = data),
      error: (error) => console.error('Error while searching for this user:', error),
    });
  }

  sendFriendRequest(userId: number): void {
    this.friendsService.sendFriendRequest(userId).subscribe({
      next: (dto) => {
        // Falls automatisch akzeptiert wurde (reverse request), sofort als connected markieren
        if (dto.status === 'ACCEPTED') {
          this.searchResults = this.searchResults.map((user) =>
            user.id === userId ? {...user, connected: true} : user
          );
          this.loadFriends();
        }
      },
      error: (error) => console.error('Error while sending friend request:', error),
    });
  }

  acceptRequest(requestId: number): void {
    this.friendsService.acceptFriendRequest(requestId).subscribe({
      next: () => {
        this.incomingRequests = this.incomingRequests.filter((r) => r.id !== requestId);
        this.loadFriends();
      },
      error: (error) => console.error('Error while accepting friend request:', error),
    });
  }

  declineRequest(requestId: number): void {
    this.friendsService.declineFriendRequest(requestId).subscribe({
      next: () => {
        this.incomingRequests = this.incomingRequests.filter((r) => r.id !== requestId);
      },
      error: (error) => console.error('Error while declining friend request:', error),
    });
  }

  removeFriend(userId: number): void {
    this.friendsService.removeFriend(userId).subscribe({
      next: () => {
        // updates the friend-list
        this.friendsService.getFriends().subscribe((data) => {
          this.friends = data;
        });
        // goes through every user in searchResults if this is the just added-user, change connected to false
        this.searchResults = this.searchResults.map((user) =>
          user.id === userId ? {...user, connected: false} : user
        );
      },
      error: (error) => {
        console.error('Error while removing friend:', error);
      }
    });
  }
}
