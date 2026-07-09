import {Component, inject, OnInit} from '@angular/core';
import {Friend, FriendSearchResult, FriendsService} from '../../services/friends.service';
import {FormsModule} from '@angular/forms';
import {NotificationService} from '../notification-menu/notification.service';
import {FeedbackService, apiErrorMessage} from '../../services/feedback.service';

@Component({
  selector: 'app-friends',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './friends.html',
  styleUrls: ['./friends.css'],
})

export class Friends implements OnInit {
  private readonly friendsService = inject(FriendsService);
  private readonly feedback = inject(FeedbackService);
  private readonly notificationService = inject(NotificationService);

  friends: Friend[] = [];
  searchResults: FriendSearchResult[] = [];
  searchName = '';
  hasSearched = false;

  ngOnInit() {
    this.friendsService.getFriends().subscribe({
      next: (data) => {
        this.friends = data;
      },
      error: (error) => {
        this.feedback.error(apiErrorMessage(error, 'Friends could not be loaded'));
      }
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
      next: (data) => {
        this.searchResults = data;
      },
      error: (error) => {
        this.feedback.error(apiErrorMessage(error, 'User search failed'));
      }
    });
  }

  addFriend(userId: number): void {
    const friendName = this.displayName(userId);
    this.friendsService.addFriend(userId).subscribe({
      next: () => {
        // updates the friend-list
        this.friendsService.getFriends().subscribe((data) => {
          this.friends = data;
        });
        // goes through every user in searchResults if this is the just added-user, change connected to true
        this.searchResults = this.searchResults.map((user) =>
          user.id === userId ? { ...user, connected: true } : user
        );
        this.feedback.success(`${friendName} added as friend`);
        this.notificationService.add(`${friendName} was added to your friends`);
      },
      error: (error) => {
        this.feedback.error(
          `Friend can't be added: ${apiErrorMessage(error, 'server not reachable')}`
        );
      }
    });
  }

  removeFriend(userId: number): void {
    const friendName = this.displayName(userId);
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
        this.feedback.success(`${friendName} removed from friends`);
        this.notificationService.add(`${friendName} was removed from your friends`);
      },
      error: (error) => {
        this.feedback.error(
          `Friend can't be removed: ${apiErrorMessage(error, 'server not reachable')}`
        );
      }
    });
  }

  // The acted-on user is in the friend list or in the current search results.
  private displayName(userId: number): string {
    const user =
      this.friends.find((friend) => friend.id === userId) ??
      this.searchResults.find((result) => result.id === userId);
    return user?.username ?? 'Friend';
  }
}
