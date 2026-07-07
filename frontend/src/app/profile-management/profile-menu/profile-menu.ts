import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { ChangePasswordDialog } from '../change-password-dialog/change-password-dialog';
import { DeleteAccountDialog } from '../delete-account-dialog/delete-account-dialog';
import { ProfileDetailsDialog } from '../profile-details-dialog/profile-details-dialog';
import { NotificationService } from '../../components/notification-menu/notification.service';
import { FeedbackService } from '../../services/feedback.service';

/**
 * Account icon in the app bar. Opens a dropdown that launches the profile dialogs: view privacy
 * details, change password, and delete account. When a dialog finishes successfully the outcome
 * is confirmed with a toast and, for a password change, recorded in the notification bell.
 */
@Component({
  selector: 'app-profile-menu',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatMenuModule],
  templateUrl: './profile-menu.html',
  styleUrl: './profile-menu.css'
})
export class ProfileMenu {
  private readonly dialog = inject(MatDialog);
  private readonly feedback = inject(FeedbackService);
  private readonly notificationService = inject(NotificationService);

  openPrivacyDetails(): void {
    this.dialog.open(ProfileDetailsDialog);
  }

  openChangePassword(): void {
    // The dialog closes with the backend's confirmation message on success, undefined on cancel.
    this.dialog.open(ChangePasswordDialog).afterClosed().subscribe((message?: string) => {
      if (message) {
        this.feedback.success(message);
        this.notificationService.add('Password changed');
      }
    });
  }

  openDeleteAccount(): void {
    // The dialog closes with 'deleted' after a successful deletion (it already clears the
    // session and navigates to the login page). Notifications are wiped too, so nothing of the
    // deleted account lingers on a shared computer; the toast outlives the navigation.
    this.dialog.open(DeleteAccountDialog).afterClosed().subscribe((result?: string) => {
      if (result === 'deleted') {
        this.notificationService.clearAll();
        this.feedback.success('Your account was deleted. Goodbye!');
      }
    });
  }
}
