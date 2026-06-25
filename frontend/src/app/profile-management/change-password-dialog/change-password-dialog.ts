import { Component, ElementRef, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { GoogleVerification } from '../google-verification';
import { ChangePasswordPayload, ProfileManagementService } from '../profile-management.service';

/** How the user proves the password change is really them: an emailed code or a Google sign-in. */
type VerifyMethod = 'code' | 'google';

/**
 * Dialog for changing the logged-in user's password. The user picks a new password and then confirms
 * ownership of the account with a one-time email code or by signing in with the linked Google
 * account; the backend ({@code POST /api/profile/change-password}) enforces that verification.
 */
@Component({
  selector: 'app-change-password-dialog',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './change-password-dialog.html',
  styleUrl: './change-password-dialog.css'
})
export class ChangePasswordDialog {
  private readonly dialogRef = inject(MatDialogRef<ChangePasswordDialog>);
  private readonly profileService = inject(ProfileManagementService);
  private readonly googleVerification = inject(GoogleVerification);

  private readonly googleButton = viewChild<ElementRef<HTMLElement>>('googleButton');

  method = signal<VerifyMethod>('code');
  codeSent = signal(false);
  message = signal('');
  error = signal('');

  newPassword = '';
  repeatNewPassword = '';
  code = '';

  constructor() {
    // Render the Google button whenever the Google verification option is selected.
    effect(() => {
      const container = this.googleButton();
      if (this.method() === 'google' && container) {
        this.googleVerification.renderButton(container.nativeElement, (idToken) =>
          this.changeWithGoogle(idToken)
        );
      }
    });
  }

  setMethod(method: VerifyMethod): void {
    this.method.set(method);
    this.message.set('');
    this.error.set('');
  }

  sendCode(): void {
    this.profileService.sendChangeCode().subscribe({
      next: (response) => {
        this.message.set(response.message);
        this.error.set('');
        this.codeSent.set(true);
      },
      error: (response) => this.showError(response)
    });
  }

  changeWithCode(): void {
    if (!this.passwordsValid()) {
      return;
    }
    this.submit({
      newPassword: this.newPassword,
      repeatNewPassword: this.repeatNewPassword,
      code: this.code
    });
  }

  private changeWithGoogle(idToken: string): void {
    if (!this.passwordsValid()) {
      return;
    }
    this.submit({
      newPassword: this.newPassword,
      repeatNewPassword: this.repeatNewPassword,
      googleIdToken: idToken
    });
  }

  private submit(payload: ChangePasswordPayload): void {
    this.profileService.changePassword(payload).subscribe({
      next: (response) => this.dialogRef.close(response.message),
      error: (response) => this.showError(response)
    });
  }

  private passwordsValid(): boolean {
    if (this.newPassword !== this.repeatNewPassword) {
      this.message.set('');
      this.error.set('Passwords do not match');
      return false;
    }
    return true;
  }

  close(): void {
    this.dialogRef.close();
  }

  private showError(response: { error?: { message?: string } }): void {
    this.message.set('');
    this.error.set(response.error?.message ?? 'Something went wrong');
  }
}
