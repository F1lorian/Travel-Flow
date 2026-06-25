import { Injectable, NgZone, inject } from '@angular/core';

// Google OAuth Web client id, supplied at runtime via public/config.js
// (regenerated from the GOOGLE_OAUTH_CLIENT_ID env var in Docker, see .env).
const GOOGLE_CLIENT_ID =
  (globalThis as { __APP_CONFIG__?: { googleClientId?: string } }).__APP_CONFIG__?.googleClientId ??
  '';

// `google` is loaded globally by the Google Identity Services script in index.html.
declare const google: { accounts?: { id?: any } } | undefined;

/** Number of milliseconds to wait before retrying when the GIS script has not loaded yet. */
const GIS_RETRY_MS = 200;

/**
 * Thin wrapper around Google Identity Services, shared by the profile dialogs that let a user
 * re-confirm ownership of their account with Google (change password, delete account).
 *
 * It hides the global `google.accounts.id` handling: waiting for the script to load, (re-)wiring the
 * sign-in callback, and re-entering Angular's zone so component state updates are detected.
 */
@Injectable({ providedIn: 'root' })
export class GoogleVerification {
  private readonly zone = inject(NgZone);

  /**
   * Renders the Google "continue with" button into `container`. When the user signs in, `onIdToken`
   * is invoked inside Angular's zone with the returned ID token, which the backend verifies against
   * the account email.
   *
   * Safe to call repeatedly: the callback is re-bound on every call, so the most recently rendered
   * button always drives the latest handler.
   */
  renderButton(container: HTMLElement, onIdToken: (idToken: string) => void): void {
    if (typeof google === 'undefined' || !google?.accounts?.id) {
      // The GIS script has not finished loading yet; retry shortly.
      setTimeout(() => this.renderButton(container, onIdToken), GIS_RETRY_MS);
      return;
    }

    google.accounts.id.initialize({
      client_id: GOOGLE_CLIENT_ID,
      // The GIS callback fires outside Angular's zone, so re-enter it before notifying the caller.
      callback: (response: { credential: string }) =>
        this.zone.run(() => onIdToken(response.credential))
    });

    container.innerHTML = '';
    google.accounts.id.renderButton(container, {
      theme: 'outline',
      size: 'large',
      width: 280,
      text: 'continue_with'
    });
  }
}
