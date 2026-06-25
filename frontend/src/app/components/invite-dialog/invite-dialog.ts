import {Component, inject, OnInit} from '@angular/core';
import {Friend, FriendsService} from '../../services/friends.service';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';

interface InviteDialogData {
  tripId: number;
}
@Component({
  selector: 'app-invite-dialog',
  imports: [FormsModule],
  standalone: true,
  templateUrl: './invite-dialog.html',
  styleUrl: './invite-dialog.css',
})
export class InviteDialog implements OnInit {
  private readonly friendsService = inject(FriendsService);
  friends: Friend[] = [];
  selectedFriendId: number | null = null;
  readonly data = inject<InviteDialogData>(MAT_DIALOG_DATA);

  ngOnInit() {
    this.friendsService.getFriends().subscribe((data) => {
        this.friends = data;
      },
      (error) => {
        console.error('Error while loading friends:', error);
      }
    )
  }
  inviteSelectedFriend(): void {
    if (this.selectedFriendId === null) {
      return;
    }

    this.friendsService.sendIviteMessage(this.data.tripId, this.selectedFriendId).subscribe({
      next: () => console.log('Invite sent'),
      error: (error) => {
        console.error('Error while sending invite:', error);
      }
    });
  }
}
