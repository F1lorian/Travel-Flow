import { CommonModule } from '@angular/common';
import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import {
  EventInviteDTO,
  Friend,
  FriendRequestDTO,
  FriendSearchResult,
  FriendsService,
  TripInviteDTO,
} from '../../services/friends.service';
import {
  PrivacyService,
  SharedEvent,
  SharedUserProfile,
} from '../../services/privacy.service';
import { WebSocketService } from '../../services/websocket.service';
import { NotificationService } from '../notification-menu/notification.service';
import { FeedbackService, apiErrorMessage } from '../../services/feedback.service';

/**
 * Friends page component.
 *
 * Allows users to:
 * - search for other users
 * - send and handle friend requests
 * - view and remove existing friends
 * - handle incoming trip and event invitations
 * - view shared profile information if permission was granted
 * - view shared events if permission was granted
 */
@Component({
  selector: 'app-friends',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './friends.html',
  styleUrls: ['./friends.css'],
})
export class Friends implements OnInit, OnDestroy {
  private readonly friendsService = inject(FriendsService);
  private readonly privacyService = inject(PrivacyService);
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

  selectedProfile: SharedUserProfile | null = null;
  sharedEvents: SharedEvent[] = [];

  privacyErrorMessage = '';
  privacySuccessMessage = '';
  loadingProfile = false;
  loadingEvents = false;

  private subscriptions = new Subscription();

  ngOnInit(): void {
    this.loadFriends();
    this.loadIncomingRequests();
    this.loadIncomingTripInvites();
    this.loadIncomingEventInvites();

    this.subscriptions.add(
      this.webSocketService.newFriendRequest$.subscribe((request) => {
        this.incomingRequests = [...this.incomingRequests, request];
      })
    );

    this.subscriptions.add(
      this.webSocketService.friendRequestAnswered$.subscribe((request) => {
        if (request.status === 'ACCEPTED') {
          this.loadFriends();
        }

        this.incomingRequests = this.incomingRequests.filter(
          (incomingRequest) => incomingRequest.id !== request.id
        );
      })
    );

    this.subscriptions.add(
      this.webSocketService.friendRemoved$.subscribe((removedByUserId) => {
        this.friends = this.friends.filter((friend) => friend.id !== removedByUserId);
        this.searchResults = this.searchResults.map((user) =>
          user.id === removedByUserId ? { ...user, connected: false } : user
        );

        if (this.selectedProfile?.id === removedByUserId) {
          this.clearSharedInformation();
        }
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
          (existingInvite) => existingInvite.tripId !== invite.tripId
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
          (existingInvite) => existingInvite.eventId !== invite.eventId
        );
      })
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  private loadFriends(): void {
    this.friendsService.getFriends().subscribe({
      next: (data) => {
        this.friends = data;
      },
      error: (error) => {
        console.error('Error while loading friends:', error);
      },
    });
  }

  private loadIncomingRequests(): void {
    this.friendsService.getIncomingRequests().subscribe({
      next: (data) => {
        this.incomingRequests = data;
      },
      error: (error) => {
        console.error('Error while loading incoming requests:', error);
      },
    });
  }

  private loadIncomingTripInvites(): void {
    this.friendsService.getIncomingTripInvites().subscribe({
      next: (data) => {
        this.incomingTripInvites = data;
      },
      error: (error) => {
        console.error('Error while loading trip invites:', error);
      },
    });
  }

  private loadIncomingEventInvites(): void {
    this.friendsService.getIncomingEventInvites().subscribe({
      next: (data) => {
        this.incomingEventInvites = data;
      },
      error: (error) => {
        console.error('Error while loading event invites:', error);
      },
    });
  }

  searchUsers(): void {
    const name = this.searchName.trim();

    if (!name) {
      this.searchResults = [];
      this.hasSearched = false;
      return;
    }

    this.hasSearched = true;
    this.friendsService.searchUsers(name).subscribe({
      next: (data) => {
        this.searchResults = data;
      },
      error: (error) => {
        console.error('Error while searching for this user:', error);
      },
    });
  }

  sendFriendRequest(userId: number): void {
    const name = this.searchResults.find((user) => user.id === userId)?.username ?? 'User';
    this.friendsService.sendFriendRequest(userId).subscribe({
      next: (dto) => {
        this.requestSent = { ...this.requestSent, [userId]: true };

        if (dto.status === 'ACCEPTED') {
          this.searchResults = this.searchResults.map((user) =>
            user.id === userId ? { ...user, connected: true } : user
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

  acceptRequest(requestId: number): void {
    const request = this.incomingRequests.find((r) => r.id === requestId);
    this.friendsService.acceptFriendRequest(requestId).subscribe({
      next: () => {
        this.incomingRequests = this.incomingRequests.filter(
          (request) => request.id !== requestId
        );
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
        this.loadFriends();

        this.searchResults = this.searchResults.map((user) =>
          user.id === userId ? { ...user, connected: false } : user
        );
        this.feedback.success(`${name} removed from friends`);
        this.notificationService.add(`${name} was removed from your friends`);

        if (this.selectedProfile?.id === userId) {
          this.clearSharedInformation();
        }
      },
      error: (error) => {
        this.feedback.error(apiErrorMessage(error, 'Friend could not be removed'));
      },
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

  /**
   * Loads profile information shared by a friend.
   *
   * The backend returns the profile only if the friend granted permission.
   */
  viewProfile(userId: number): void {
    this.privacyErrorMessage = '';
    this.privacySuccessMessage = '';
    this.loadingProfile = true;

    this.privacyService.getSharedProfile(userId).subscribe({
      next: (profile) => {
        this.selectedProfile = profile;
        this.loadingProfile = false;
        this.privacySuccessMessage = 'Profile information loaded.';
      },
      error: (error) => {
        console.error('Error while loading shared profile:', error);
        this.selectedProfile = null;
        this.loadingProfile = false;
        this.privacyErrorMessage =
          'You do not have permission to view this profile information.';
      },
    });
  }

  /**
   * Loads events shared by a friend.
   *
   * The backend returns the events only if the friend granted permission.
   */
  viewEvents(userId: number): void {
    this.privacyErrorMessage = '';
    this.privacySuccessMessage = '';
    this.loadingEvents = true;

    this.privacyService.getSharedEvents(userId).subscribe({
      next: (events) => {
        this.sharedEvents = events;
        this.loadingEvents = false;
        this.privacySuccessMessage = 'Shared events loaded.';
      },
      error: (error) => {
        console.error('Error while loading shared events:', error);
        this.sharedEvents = [];
        this.loadingEvents = false;
        this.privacyErrorMessage = 'You do not have permission to view these events.';
      },
    });
  }

  clearSharedInformation(): void {
    this.selectedProfile = null;
    this.sharedEvents = [];
    this.privacyErrorMessage = '';
    this.privacySuccessMessage = '';
  }
}
