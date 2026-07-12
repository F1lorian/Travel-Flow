import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  PrivacyPermission,
  PrivacyService,
  UpdatePrivacyPermissionRequest
} from '../../services/privacy.service';

/**
 * Privacy settings page.
 *
 * Allows the current user to configure which friends can see:
 * - events
 * - profile information
 */
@Component({
  selector: 'app-privacy-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './privacy-settings.html',
  styleUrl: './privacy-settings.css'
})
export class PrivacySettings implements OnInit {
  private readonly privacyService = inject(PrivacyService);

  // List of friends and their current privacy permissions.
  permissions: PrivacyPermission[] = [];

  // Tracks which permission rows are currently being saved.
  // This allows disabling only the row that is being updated.
  savingFriendIds = new Set<number>();

  // UI state for loading and feedback messages.
  loading = false;
  errorMessage = '';
  successMessage = '';

  /**
   * Loads privacy permissions when the page is opened.
   */
  ngOnInit(): void {
    this.loadPermissions();
  }

  /**
   * Loads all privacy permissions for the current user.
   *
   * The backend returns one entry per friend.
   */
  loadPermissions(): void {
    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.privacyService.getPermissions().subscribe({
      next: (permissions) => {
        this.permissions = permissions;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error while loading privacy permissions:', error);
        this.errorMessage = 'Privacy settings could not be loaded.';
        this.loading = false;
      }
    });
  }

  /**
   * Updates whether a friend can see the current user's events.
   *
   * The existing permission object is copied so that only canSeeEvents changes.
   */
  updateEventsPermission(permission: PrivacyPermission, canSeeEvents: boolean): void {
    const updatedPermission: PrivacyPermission = {
      ...permission,
      canSeeEvents
    };

    this.savePermission(updatedPermission);
  }

  /**
   * Updates whether a friend can see the current user's profile information.
   *
   * The existing permission object is copied so that only canSeeProfile changes.
   */
  updateProfilePermission(permission: PrivacyPermission, canSeeProfile: boolean): void {
    const updatedPermission: PrivacyPermission = {
      ...permission,
      canSeeProfile
    };

    this.savePermission(updatedPermission);
  }

  /**
   * Checks if a specific friend row is currently being saved.
   */
  isSaving(friendId: number): boolean {
    return this.savingFriendIds.has(friendId);
  }

  /**
   * Saves the updated permission for one friend.
   *
   * After a successful update, only the changed row is updated locally.
   * If saving fails, the permissions are reloaded from the backend to restore consistency.
   */
  private savePermission(permission: PrivacyPermission): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.savingFriendIds.add(permission.friendId);

    const request: UpdatePrivacyPermissionRequest = {
      canSeeEvents: permission.canSeeEvents,
      canSeeProfile: permission.canSeeProfile
    };

    this.privacyService.updatePermission(permission.friendId, request).subscribe({
      next: (updatedPermission) => {
        this.permissions = this.permissions.map((existingPermission) =>
          existingPermission.friendId === updatedPermission.friendId
            ? updatedPermission
            : existingPermission
        );

        this.savingFriendIds.delete(permission.friendId);
        this.successMessage = 'Privacy settings have been updated.';
      },
      error: (error) => {
        console.error('Error while updating privacy permission:', error);
        this.savingFriendIds.delete(permission.friendId);
        this.errorMessage = 'Privacy settings could not be updated.';

        // Reload permissions to undo any optimistic UI changes.
        this.loadPermissions();
      }
    });
  }
}
