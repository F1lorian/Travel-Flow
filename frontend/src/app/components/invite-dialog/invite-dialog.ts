import { Component, inject, OnInit } from '@angular/core';
import { Friend, FriendsService } from '../../services/friends.service';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NotificationService } from '../notification-menu/notification.service';
import { FeedbackService, apiErrorMessage } from '../../services/feedback.service';

interface InviteDialogData {
  tripId: number;
}

/**
 * Dialog for inviting one of the user's friends to the current trip. On success the dialog closes
 * and the user gets a toast plus a bell notification; the invited friend is notified by email
 * (sent by the backend). On failure the backend's reason is shown, e.g. "User is not a friend".
 */
@Component({
  selector: 'app-invite-dialog',
  imports: [FormsModule],
  standalone: true,
  templateUrl: './invite-dialog.html',
  styleUrl: './invite-dialog.css',
})
export class InviteDialog implements OnInit {
  private readonly friendsService = inject(FriendsService);
  private readonly feedback = inject(FeedbackService);
  private readonly notificationService = inject(NotificationService);
  private readonly dialogRef = inject(MatDialogRef<InviteDialog>);
  readonly data = inject<InviteDialogData>(MAT_DIALOG_DATA);

  friends: Friend[] = [];
  selectedFriendId: number | null = null;

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

  inviteSelectedFriend(): void {
    if (this.selectedFriendId === null) {
      return;
    }
    const friend = this.friends.find(f => f.id === this.selectedFriendId);
    const friendName = friend?.username ?? 'friend';

    this.friendsService.sendIviteMessage(this.data.tripId, this.selectedFriendId).subscribe({
      next: () => {
        this.feedback.success(`Invite sent to ${friendName}`);
        this.notificationService.add(`${friendName} was invited to the trip`);
        this.dialogRef.close('invited');
      },
      error: (error) => {
        this.feedback.error(
          `Invite can't be sent: ${apiErrorMessage(error, 'server not reachable')}`
        );
      }
    });
  }
}
