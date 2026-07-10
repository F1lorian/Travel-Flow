import {Component, inject, OnInit} from '@angular/core';
import {Friend, FriendsService} from '../../services/friends.service';
import { FormsModule } from '@angular/forms';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {MatIcon} from '@angular/material/icon';
import {SlicePipe} from '@angular/common';
import {forkJoin} from 'rxjs';
import {NotificationService} from '../notification-menu/notification.service';
import {FeedbackService, apiErrorMessage} from '../../services/feedback.service';

interface InviteDialogData {
  tripId: number;
}

/**
 * Dialog for inviting one or more of the user's friends to the current trip. On success the dialog
 * closes and the user gets a toast plus a bell notification per invited friend; each invited friend
 * is notified by email (sent by the backend). On failure the backend's reason is shown.
 */
@Component({
  selector: 'app-invite-dialog',
  imports: [FormsModule, MatIcon, SlicePipe],
  standalone: true,
  templateUrl: './invite-dialog.html',
  styleUrl: './invite-dialog.css',
})
export class InviteDialog implements OnInit {
  private readonly dialogRef = inject(MatDialogRef<InviteDialog>);
  private readonly friendsService = inject(FriendsService);
  private readonly feedback = inject(FeedbackService);
  private readonly notificationService = inject(NotificationService);
  readonly data = inject<InviteDialogData>(MAT_DIALOG_DATA);

  friends: Friend[] = [];
  errorMessage = "";
  inviteDropdownOpen = false;
  selection: Friend[] = [];

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

  /**
   * sends invites to all the friends that have been selected, closes the dialog after that
   */
  inviteSelectedFriend(): void {
    if (this.selection.length === 0) {
      return;
    }

    const invitedFriends = this.selection;
    const inviteCalls = invitedFriends.map(friend =>
      this.friendsService.sendInviteMessage(this.data.tripId, friend.id)
    );

    forkJoin(inviteCalls).subscribe({
      next: () => {
        this.errorMessage = '';
        const names = invitedFriends.map(friend => friend.username).join(', ');
        this.feedback.success(`Invite sent to ${names}`);
        for (const friend of invitedFriends) {
          this.notificationService.add(`${friend.username} was invited to the trip`);
        }
      },
      error: (error) => {
        this.errorMessage = apiErrorMessage(error, "Invitation couldn't be sent.");
        this.feedback.error(`Invite can't be sent: ${this.errorMessage}`);
      }
    });

    this.close();
  }

  selectFriend(friend: Friend) {
    if (!this.selection.some(f => f.id === friend.id)) {
      this.selection = [...this.selection, friend];
    }
  }

  removeSelection(friend: Friend) {
    this.selection = this.selection.filter(f => f.id !== friend.id);
  }

  close(){
    this.dialogRef.close();
  }
}
