import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { GmailService } from '../../services/gmail.service';

/** sessionStorage key holding the CSRF state generated before the Google redirect. */
export const GMAIL_OAUTH_STATE_KEY = 'gmailOAuthState';

/**
 * Landing page for the Google OAuth redirect (route `/gmail-import`). Verifies the CSRF `state`,
 * exchanges the authorization code via the backend, then returns the user to the calendar.
 */
@Component({
  selector: 'app-gmail-import',
  imports: [],
  templateUrl: './gmail-import.html',
})
export class GmailImport implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly gmail = inject(GmailService);

  readonly state = signal<'connecting' | 'success' | 'error'>('connecting');
  readonly message = signal('Connecting your Gmail account…');

  ngOnInit() {
    const params = this.route.snapshot.queryParamMap;
    const code = params.get('code');
    const returnedState = params.get('state');
    const oauthError = params.get('error');

    const expectedState = sessionStorage.getItem(GMAIL_OAUTH_STATE_KEY);
    sessionStorage.removeItem(GMAIL_OAUTH_STATE_KEY);

    if (oauthError) {
      this.fail('Gmail access was denied.');
      return;
    }
    if (!code || !returnedState || returnedState !== expectedState) {
      this.fail('Gmail connection could not be verified. Please try connecting again.');
      return;
    }

    this.gmail.connect(code).subscribe({
      next: (connection) => {
        this.state.set('success');
        this.message.set(`Connected ${connection.email ?? 'Gmail'}. Returning to the calendar…`);
        setTimeout(() => this.router.navigate(['/calendar']), 1200);
      },
      error: (err) => this.fail(err.error || 'Could not connect your Gmail account.'),
    });
  }

  backToCalendar() {
    this.router.navigate(['/calendar']);
  }

  private fail(message: string) {
    this.state.set('error');
    this.message.set(message);
  }
}
