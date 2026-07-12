import { SlicePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogContent,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import { Friend, FriendsService } from '../../services/friends.service';

interface InviteDialogData {
  tripId: number;
}

@Component({
  selector: 'app-invite-dialog',
  imports: [FormsModule, MatIcon, SlicePipe, MatDialogContent, MatDialogActions],
  standalone: true,
  templateUrl: './invite-dialog.html',
  styleUrl: './invite-dialog.css',
})
export class InviteDialog implements OnInit {
  private readonly dialogRef = inject(MatDialogRef<InviteDialog>);
  private readonly friendsService = inject(FriendsService);
  readonly data = inject<InviteDialogData>(MAT_DIALOG_DATA);

  friends: Friend[] = [];
  errorMessage = '';
  inviteDropdownOpen = false;
  selection: Friend[] = [];
  selectedFriendId: number | null = null;

  ngOnInit(): void {
    this.friendsService.getFriends().subscribe({
      next: (data) => {
        this.friends = data;
      },
      error: (error: unknown) => {
        console.error('Error while loading friends:', error);
      },
    });
  }

  /**
   * Sends invites to all selected friends.
   *
   * If the template uses a single dropdown, selectedFriendId is used.
   * If the template uses multi-select, selection is used.
   */
  inviteSelectedFriend(): void {
    const selectedFriends = this.getSelectedFriends();

    if (selectedFriends.length === 0) {
      return;
    }

    const inviteCalls = selectedFriends.map((friend) =>
      this.friendsService.sendInviteMessage(this.data.tripId, friend.id)
    );

    forkJoin(inviteCalls).subscribe({
      next: () => {
        this.errorMessage = '';
        this.close();
      },
      error: (error: HttpErrorResponse) => {
        console.error('Error while sending invite:', error);
        this.errorMessage = error.error?.message || 'Invitation could not be sent.';
      },
    });
  }

  selectFriend(friend: Friend): void {
    if (!this.selection.some((selectedFriend) => selectedFriend.id === friend.id)) {
      this.selection = [...this.selection, friend];
    }
  }

  removeSelection(friend: Friend): void {
    this.selection = this.selection.filter((selectedFriend) => selectedFriend.id !== friend.id);
  }

  close(): void {
    this.dialogRef.close();
  }

  private getSelectedFriends(): Friend[] {
    if (this.selection.length > 0) {
      return this.selection;
    }

    if (this.selectedFriendId === null) {
      return [];
    }

    return this.friends.filter((friend) => friend.id === this.selectedFriendId);
  }
}
