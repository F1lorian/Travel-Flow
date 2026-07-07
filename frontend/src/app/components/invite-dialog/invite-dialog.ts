import {Component, inject, OnInit} from '@angular/core';
import {Friend, FriendsService} from '../../services/friends.service';
import { FormsModule } from '@angular/forms';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {MatIcon} from '@angular/material/icon';
import {SlicePipe} from '@angular/common';
import {forkJoin} from 'rxjs';

interface InviteDialogData {
  tripId: number;
}
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
  friends: Friend[] = [];
  errorMessage: String = "";
  readonly data = inject<InviteDialogData>(MAT_DIALOG_DATA);
  inviteDropdownOpen = false;
  selection: Friend[] = [];


  ngOnInit() {
    this.friendsService.getFriends().subscribe((data) => {
        this.friends = data;
      },
      (error) => {
        console.error('Error while loading friends:', error);
      }
    )
  }

  /**
   * sends invites to all the friends that have been selected, closes the dialog after that
   */
  inviteSelectedFriend(): void {
    if (this.selection.length === 0) {
      return;
    }

    const inviteCalls = this.selection.map(friend => this.friendsService.sendInviteMessage(this.data.tripId, friend.id));

    forkJoin(inviteCalls).subscribe({
      next: () => {
        console.log('Invite sent');
        this.errorMessage = '';
      },
      error: (error) => {
        console.error('Error while sending invite:', error);
        this.errorMessage = error?.error?.message || 'Einladung konnte nicht gesendet werden.';
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
