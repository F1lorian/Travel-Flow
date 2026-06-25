import { Component, ElementRef, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { GoogleVerification } from '../google-verification';
import { DeleteAccountPayload, ProfileManagementService } from '../profile-management.service';

/** How the user proves the deletion is really them: an emailed code or a Google sign-in. */
type VerifyMethod = 'code' | 'google';

/**
 * Dialog for permanently deleting the logged-in user's account. The flow is two-staged: an explicit
 * confirmation, then ownership verification with a one-time email code or a Google sign-in. On
 * success ({@code DELETE /api/profile}) the local session is cleared and the user is sent back to
 * the login screen, since the account no longer exists.
 */
@Component({
  selector: 'app-delete-account-dialog',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './delete-account-dialog.html',
  styleUrl: './delete-account-dialog.css'
})
export class DeleteAccountDialog {
  private readonly dialogRef = inject(MatDialogRef<DeleteAccountDialog>);
  private readonly profileService = inject(ProfileManagementService);
  private readonly googleVerification = inject(GoogleVerification);
  private readonly router = inject(Router);

  private readonly googleButton = viewChild<ElementRef<HTMLElement>>('googleButton');

  method = signal<VerifyMethod>('code');
  codeSent = signal(false);
  confirmed = signal(false);
  message = signal('');
  error = signal('');

  code = '';

  constructor() {
    // Render the Google button only once the user has confirmed and picked Google verification.
    effect(() => {
      const container = this.googleButton();
      if (this.confirmed() && this.method() === 'google' && container) {
        this.googleVerification.renderButton(container.nativeElement, (idToken) =>
          this.submit({ googleIdToken: idToken })
        );
      }
    });
  }

  setMethod(method: VerifyMethod): void {
    this.method.set(method);
    this.message.set('');
    this.error.set('');
  }

  confirm(): void {
    this.confirmed.set(true);
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

  deleteWithCode(): void {
    this.submit({ code: this.code });
  }

  private submit(payload: DeleteAccountPayload): void {
    this.profileService.deleteAccount(payload).subscribe({
      next: () => {
        // The account is gone, so drop the session and return to the login screen.
        localStorage.removeItem('sepUser');
        localStorage.removeItem('sepToken');
        this.dialogRef.close('deleted');
        this.router.navigateByUrl('/account');
      },
      error: (response) => this.showError(response)
    });
  }

  close(): void {
    this.dialogRef.close();
  }

  private showError(response: { error?: { message?: string } }): void {
    this.message.set('');
    this.error.set(response.error?.message ?? 'Something went wrong');
  }
}
