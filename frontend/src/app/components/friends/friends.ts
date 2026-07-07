import {Component, inject, OnInit, OnDestroy} from '@angular/core';
import {Friend, FriendSearchResult, FriendsService, FriendRequestDTO, TripInviteDTO,
  EventInviteDTO,} from '../../services/friends.service';
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
  incomingTripInvites: TripInviteDTO[] = [];
  incomingEventInvites: EventInviteDTO[] = [];
  searchName = '';
  hasSearched = false;
  requestSent: Record<string, boolean> = {};

  private subscriptions = new Subscription();

  ngOnInit() {
    this.loadFriends();
    this.loadIncomingRequests();
    this.loadIncomingTripInvites();
    this.loadIncomingEventInvites();

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

    this.subscriptions.add(
      this.webSocketService.newTripInvite$.subscribe((invite) => {
        this.incomingTripInvites = [...this.incomingTripInvites, invite];
      })
    );

    this.subscriptions.add(
      this.webSocketService.tripInviteAnswered$.subscribe((invite) => {
        this.incomingTripInvites = this.incomingTripInvites.filter(
          (i) => i.tripId !== invite.tripId
        );
      })
    );

    this.subscriptions.add(
      this.webSocketService.newEventInvite$.subscribe((invite) => {
        this.incomingEventInvites = [...this.incomingEventInvites, invite];
      })
    );

    this.subscriptions.add(
      this.webSocketService.eventInviteAnswered$.subscribe((invite) => {
        this.incomingEventInvites = this.incomingEventInvites.filter(
          (i) => i.eventId !== invite.eventId
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

  private loadIncomingTripInvites(): void {
    this.friendsService.getIncomingTripInvites().subscribe({
      next: (data) => (this.incomingTripInvites = data),
      error: (error) => console.error('Error while loading trip invites:', error),
    });
  }

  private loadIncomingEventInvites(): void {
    this.friendsService.getIncomingEventInvites().subscribe({
      next: (data) => (this.incomingEventInvites = data),
      error: (error) => console.error('Error while loading event invites:', error),
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
        this.requestSent = { ...this.requestSent, [userId]: true };
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

  /**
   * accepting the friendrequest
   * @param requestId
   */
  acceptRequest(requestId: number): void {
    this.friendsService.acceptFriendRequest(requestId).subscribe({
      next: () => {
        this.incomingRequests = this.incomingRequests.filter((r) => r.id !== requestId);
        this.loadFriends();
      },
      error: (error) => console.error('Error while accepting friend request:', error),
    });
  }

  /**
   * declining the friendrequest
   * @param requestId
   */
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

  acceptTripInvite(tripId: number): void {
    this.friendsService.acceptTripInvite(tripId).subscribe({
      next: () => {
        this.incomingTripInvites = this.incomingTripInvites.filter((i) => i.tripId !== tripId);
      },
      error: (error) => console.error('Error while accepting trip invite:', error),
    });
  }

  declineTripInvite(tripId: number): void {
    this.friendsService.declineTripInvite(tripId).subscribe({
      next: () => {
        this.incomingTripInvites = this.incomingTripInvites.filter((i) => i.tripId !== tripId);
      },
      error: (error) => console.error('Error while declining trip invite:', error),
    });
  }

  acceptEventInvite(eventId: number): void {
    this.friendsService.acceptEventInvite(eventId).subscribe({
      next: () => {
        this.incomingEventInvites = this.incomingEventInvites.filter((i) => i.eventId !== eventId);
      },
      error: (error) => console.error('Error while accepting event invite:', error),
    });
  }

  declineEventInvite(eventId: number): void {
    this.friendsService.declineEventInvite(eventId).subscribe({
      next: () => {
        this.incomingEventInvites = this.incomingEventInvites.filter((i) => i.eventId !== eventId);
      },
      error: (error) => console.error('Error while declining event invite:', error),
    });
  }
}
