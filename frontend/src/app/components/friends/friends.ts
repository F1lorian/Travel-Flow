import {Component, inject, OnInit, OnDestroy} from '@angular/core';
import {Friend, FriendSearchResult, FriendsService, FriendRequestDTO, TripInviteDTO,
  EventInviteDTO,} from '../../services/friends.service';
import {FormsModule} from '@angular/forms';
import {WebSocketService} from '../../services/websocket.service';
import {Subscription} from 'rxjs';
import {NotificationService} from '../notification-menu/notification.service';
import {FeedbackService, apiErrorMessage} from '../../services/feedback.service';

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
  private readonly feedback = inject(FeedbackService);
  private readonly notificationService = inject(NotificationService);

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
    const name = this.searchResults.find((user) => user.id === userId)?.username ?? 'User';
    this.friendsService.sendFriendRequest(userId).subscribe({
      next: (dto) => {
        this.requestSent = { ...this.requestSent, [userId]: true };
        if (dto.status === 'ACCEPTED') {
          this.searchResults = this.searchResults.map((user) =>
            user.id === userId ? {...user, connected: true} : user
          );
          this.loadFriends();
          this.feedback.success(`${name} is now your friend`);
          this.notificationService.add(`${name} is now your friend`);
        } else {
          this.feedback.success(`Friend request sent to ${name}`);
          this.notificationService.add(`Friend request sent to ${name}`);
        }
      },
      error: (error) => {
        this.feedback.error(apiErrorMessage(error, 'Friend request could not be sent'));
      },
    });
  }

  /**
   * accepting the friendrequest
   * @param requestId
   */
  acceptRequest(requestId: number): void {
    const request = this.incomingRequests.find((r) => r.id === requestId);
    this.friendsService.acceptFriendRequest(requestId).subscribe({
      next: () => {
        this.incomingRequests = this.incomingRequests.filter((r) => r.id !== requestId);
        this.loadFriends();
        const name = request?.senderUsername ?? 'Friend request';
        this.feedback.success(`${name} is now your friend`);
        this.notificationService.add(`${name} is now your friend`);
      },
      error: (error) => {
        this.feedback.error(apiErrorMessage(error, 'Friend request could not be accepted'));
      },
    });
  }

  /**
   * declining the friendrequest
   * @param requestId
   */
  declineRequest(requestId: number): void {
    const request = this.incomingRequests.find((r) => r.id === requestId);
    this.friendsService.declineFriendRequest(requestId).subscribe({
      next: () => {
        this.incomingRequests = this.incomingRequests.filter((r) => r.id !== requestId);
        const name = request?.senderUsername ?? 'Friend request';
        this.feedback.success(`Declined friend request from ${name}`);
      },
      error: (error) => {
        this.feedback.error(apiErrorMessage(error, 'Friend request could not be declined'));
      },
    });
  }

  removeFriend(userId: number): void {
    const name = this.friends.find((friend) => friend.id === userId)?.username ?? 'Friend';
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
        this.feedback.success(`${name} removed from friends`);
        this.notificationService.add(`${name} was removed from your friends`);
      },
      error: (error) => {
        this.feedback.error(apiErrorMessage(error, 'Friend could not be removed'));
      }
    });
  }

  acceptTripInvite(tripId: number): void {
    const invite = this.incomingTripInvites.find((i) => i.tripId === tripId);
    this.friendsService.acceptTripInvite(tripId).subscribe({
      next: () => {
        this.incomingTripInvites = this.incomingTripInvites.filter((i) => i.tripId !== tripId);
        const name = invite?.tripName ?? 'the trip';
        this.feedback.success(`Joined trip "${name}"`);
        this.notificationService.add(`You joined the trip "${name}"`);
      },
      error: (error) => {
        this.feedback.error(apiErrorMessage(error, 'Trip invite could not be accepted'));
      },
    });
  }

  declineTripInvite(tripId: number): void {
    const invite = this.incomingTripInvites.find((i) => i.tripId === tripId);
    this.friendsService.declineTripInvite(tripId).subscribe({
      next: () => {
        this.incomingTripInvites = this.incomingTripInvites.filter((i) => i.tripId !== tripId);
        const name = invite?.tripName ?? 'the trip';
        this.feedback.success(`Declined invite to "${name}"`);
      },
      error: (error) => {
        this.feedback.error(apiErrorMessage(error, 'Trip invite could not be declined'));
      },
    });
  }

  acceptEventInvite(eventId: number): void {
    const invite = this.incomingEventInvites.find((i) => i.eventId === eventId);
    this.friendsService.acceptEventInvite(eventId).subscribe({
      next: () => {
        this.incomingEventInvites = this.incomingEventInvites.filter((i) => i.eventId !== eventId);
        const name = invite?.eventName ?? 'the event';
        this.feedback.success(`Joined event "${name}"`);
        this.notificationService.add(`You joined the event "${name}"`);
      },
      error: (error) => {
        this.feedback.error(apiErrorMessage(error, 'Event invite could not be accepted'));
      },
    });
  }

  declineEventInvite(eventId: number): void {
    const invite = this.incomingEventInvites.find((i) => i.eventId === eventId);
    this.friendsService.declineEventInvite(eventId).subscribe({
      next: () => {
        this.incomingEventInvites = this.incomingEventInvites.filter((i) => i.eventId !== eventId);
        const name = invite?.eventName ?? 'the event';
        this.feedback.success(`Declined invite to "${name}"`);
      },
      error: (error) => {
        this.feedback.error(apiErrorMessage(error, 'Event invite could not be declined'));
      },
    });
  }
}
