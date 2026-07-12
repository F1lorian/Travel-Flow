import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/**
 * Central place for the short toast messages (snackbars) that confirm or explain the outcome of a
 * user action: "Trip "Paris" created", "Trip can't be created: Trip name cannot be empty", ...
 *
 * Every action in the app reports its outcome through this service so feedback looks and behaves
 * the same everywhere (HCI golden rules: consistency + informative feedback). Milestone events are
 * additionally recorded in the notification bell via NotificationService.
 */
@Injectable({ providedIn: 'root' })
export class FeedbackService {
  private readonly snackBar = inject(MatSnackBar);

  /** Confirms a successful action, e.g. "Trip "Paris" created". */
  success(message: string): void {
    this.snackBar.open(message, 'OK', {
      duration: 3500,
      panelClass: 'feedback-success'
    });
  }

  /** Explains why an action failed; shown longer so there is time to read the reason. */
  error(message: string): void {
    this.snackBar.open(message, 'OK', {
      duration: 6500,
      panelClass: 'feedback-error'
    });
  }
}

/**
 * Extracts a human-readable reason from a failed HTTP call. The backend answers errors with a
 * {"message": "..."} body (see GlobalExceptionHandler); a few older endpoints send plain text.
 * When neither is present the given fallback is used, so the user never sees a raw HTTP error.
 */
export function apiErrorMessage(err: unknown, fallback: string): string {
  const httpError = err as { error?: { message?: string } | string };
  if (httpError?.error && typeof httpError.error === 'object' && httpError.error.message) {
    return httpError.error.message;
  }
  if (typeof httpError?.error === 'string' && httpError.error.trim().length > 0) {
    return httpError.error;
  }
  return fallback;
}
