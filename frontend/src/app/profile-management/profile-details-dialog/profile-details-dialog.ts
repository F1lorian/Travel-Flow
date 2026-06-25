import { Component, inject, signal } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { ProfileDetails, ProfileManagementService } from '../profile-management.service';

/** Read-only dialog that loads and displays the logged-in user's privacy details. */
@Component({
  selector: 'app-profile-details-dialog',
  standalone: true,
  imports: [],
  templateUrl: './profile-details-dialog.html',
  styleUrl: './profile-details-dialog.css'
})
export class ProfileDetailsDialog {
  private readonly dialogRef = inject(MatDialogRef<ProfileDetailsDialog>);
  private readonly profileService = inject(ProfileManagementService);

  profile = signal<ProfileDetails | null>(null);
  error = signal('');

  constructor() {
    this.profileService.getProfile().subscribe({
      next: (profile) => this.profile.set(profile),
      error: (response) =>
        this.error.set(response.error?.message ?? 'Could not load your profile details')
    });
  }

  close(): void {
    this.dialogRef.close();
  }
}
