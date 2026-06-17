import {Component, OnInit} from '@angular/core';
import {Friend, FriendSearchResult, FriendsService} from '../../services/friends.service';
import {FormsModule} from '@angular/forms';

@Component({
  selector: 'app-friends',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './friends.html',
  styleUrls: ['./friends.css'],
})

export class Friends implements OnInit {
  friends: Friend[] = [];
  searchResults: FriendSearchResult[] = [];
  searchName: string = '';

  constructor(
              private friendsService: FriendsService
  ) {}
  ngOnInit() {
    this.friendsService.getFriends().subscribe((data) => {
        this.friends = data;
      },
      (error) => {
        console.error('Error while loading friends:', error);
      }
    )
  }

  searchUsers(){
    const name = this.searchName.trim();
    if (!name) {
      this.searchResults = [];
      return;
    }

    this.friendsService.searchUsers(name).subscribe((data) => {
      this.searchResults = data;
    },
      (error) => {
        console.error('Error while searching for this user:', error);
      })
  }

  addFriend(userId: number): void {
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
      },
      error: (error) => {
        console.error('Error while adding friend:', error);
      }
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
