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
    this.friendsService.sendFriendRequest(userId).subscribe({
      next: (dto) => {
        this.requestSent = { ...this.requestSent, [userId]: true };

        if (dto.status === 'ACCEPTED') {
          this.searchResults = this.searchResults.map((user) =>
            user.id === userId ? { ...user, connected: true } : user
          );

          this.loadFriends();
        }
      },
      error: (error) => {
        console.error('Error while sending friend request:', error);
      },
    });
  }

  acceptRequest(requestId: number): void {
    this.friendsService.acceptFriendRequest(requestId).subscribe({
      next: () => {
        this.incomingRequests = this.incomingRequests.filter(
          (request) => request.id !== requestId
        );
        this.loadFriends();
      },
      error: (error) => {
        console.error('Error while accepting friend request:', error);
      },
    });
  }

  declineRequest(requestId: number): void {
    this.friendsService.declineFriendRequest(requestId).subscribe({
      next: () => {
        this.incomingRequests = this.incomingRequests.filter(
          (request) => request.id !== requestId
        );
      },
      error: (error) => {
        console.error('Error while declining friend request:', error);
      },
    });
  }

  removeFriend(userId: number): void {
    this.friendsService.removeFriend(userId).subscribe({
      next: () => {
        this.loadFriends();

        this.searchResults = this.searchResults.map((user) =>
          user.id === userId ? { ...user, connected: false } : user
        );

        if (this.selectedProfile?.id === userId) {
          this.clearSharedInformation();
        }
      },
      error: (error) => {
        console.error('Error while removing friend:', error);
      },
    });
  }

  acceptTripInvite(tripId: number): void {
    this.friendsService.acceptTripInvite(tripId).subscribe({
      next: () => {
        this.incomingTripInvites = this.incomingTripInvites.filter(
          (invite) => invite.tripId !== tripId
        );
      },
      error: (error) => {
        console.error('Error while accepting trip invite:', error);
      },
    });
  }

  declineTripInvite(tripId: number): void {
    this.friendsService.declineTripInvite(tripId).subscribe({
      next: () => {
        this.incomingTripInvites = this.incomingTripInvites.filter(
          (invite) => invite.tripId !== tripId
        );
      },
      error: (error) => {
        console.error('Error while declining trip invite:', error);
      },
    });
  }

  acceptEventInvite(eventId: number): void {
    this.friendsService.acceptEventInvite(eventId).subscribe({
      next: () => {
        this.incomingEventInvites = this.incomingEventInvites.filter(
          (invite) => invite.eventId !== eventId
        );
      },
      error: (error) => {
        console.error('Error while accepting event invite:', error);
      },
    });
  }

  declineEventInvite(eventId: number): void {
    this.friendsService.declineEventInvite(eventId).subscribe({
      next: () => {
        this.incomingEventInvites = this.incomingEventInvites.filter(
          (invite) => invite.eventId !== eventId
        );
      },
      error: (error) => {
        console.error('Error while declining event invite:', error);
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
