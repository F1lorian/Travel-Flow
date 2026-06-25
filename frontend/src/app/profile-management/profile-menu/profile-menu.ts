import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { ChangePasswordDialog } from '../change-password-dialog/change-password-dialog';
import { DeleteAccountDialog } from '../delete-account-dialog/delete-account-dialog';
import { ProfileDetailsDialog } from '../profile-details-dialog/profile-details-dialog';

/**
 * Account icon in the app bar. Opens a dropdown that launches the profile dialogs: view privacy
 * details, change password, and delete account.
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

  openPrivacyDetails(): void {
    this.dialog.open(ProfileDetailsDialog);
  }

  openChangePassword(): void {
    this.dialog.open(ChangePasswordDialog);
  }

  openDeleteAccount(): void {
    this.dialog.open(DeleteAccountDialog);
  }
}
