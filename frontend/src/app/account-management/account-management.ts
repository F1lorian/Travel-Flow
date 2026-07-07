import { Component, ElementRef, effect, inject, NgZone, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AccountManagementService } from './account-management.service';
import { WebSocketService } from '../services/websocket.service';

type AccountMode = 'login' | 'register' | 'verify' | 'forgot' | 'reset' | 'forgot-email' | 'forgot-code' | 'reset-email';

// Google OAuth Web client id, supplied at runtime via public/config.js
// (regenerated from the GOOGLE_OAUTH_CLIENT_ID env var in Docker, see .env).
const GOOGLE_CLIENT_ID =
  (globalThis as { __APP_CONFIG__?: { googleClientId?: string } }).__APP_CONFIG__?.googleClientId ?? '';

// Minimal shape of the `google.accounts.id` API used here, loaded globally by the Google Identity
// Services script in index.html.
interface GoogleAccountsId {
  initialize(config: {
    client_id: string;
    callback: (response: { credential: string }) => void;
  }): void;
  renderButton(
    parent: HTMLElement,
    options: { theme: string; size: string; width: number; text: string }
  ): void;
}

declare const google: { accounts?: { id?: GoogleAccountsId } } | undefined;

@Component({
  selector: 'app-account-management',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './account-management.html',
  styleUrl: './account-management.css'
})
export class AccountManagement {
  private readonly accountService = inject(AccountManagementService);
  private readonly router = inject(Router);
  private readonly zone = inject(NgZone);
  private readonly webSocketService = inject(WebSocketService);

  private readonly googleButton = viewChild<ElementRef<HTMLElement>>('googleButton');
  private googleInitialized = false;

  mode = signal<AccountMode>('login');
  message = signal('');
  error = signal('');
  loggedInUser = signal(localStorage.getItem('sepUser') ?? '');

  firstName = '';
  surname = '';
  username = '';
  email = '';
  verificationCode = '';
  password = '';
  repeatPassword = '';
  favoriteColor = '';
  newPassword = '';
  repeatNewPassword = '';
  resetCode = '';

  constructor() {
    // Render the Google button whenever the login form (and its container) is on screen.
    effect(() => {
      const container = this.googleButton();
      if (this.mode() === 'login' && container) {
        this.renderGoogleButton(container.nativeElement);
      }
    });
  }

  private renderGoogleButton(container: HTMLElement): void {
    if (typeof google === 'undefined' || !google?.accounts?.id) {
      // GSI script not loaded yet; retry shortly.
      setTimeout(() => this.renderGoogleButton(container), 200);
      return;
    }
    if (!this.googleInitialized) {
      google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        callback: (response: { credential: string }) => this.handleGoogleCredential(response)
      });
      this.googleInitialized = true;
    }
    container.innerHTML = '';
    google.accounts.id.renderButton(container, {
      theme: 'outline',
      size: 'large',
      width: 280,
      text: 'signin_with'
    });
  }

  private handleGoogleCredential(response: { credential: string }): void {
    // The GSI callback fires outside Angular's zone, so re-enter it before updating state.
    this.zone.run(() => {
      this.accountService.loginWithGoogle(response.credential).subscribe({
        next: (res) => {
          this.loggedInUser.set(res.username);
          localStorage.setItem('sepUser', res.username);
          if (res.token) {
            localStorage.setItem('sepToken', res.token);
            this.webSocketService.connect();
          }
          this.message.set(res.message);
          this.error.set('');
          this.router.navigateByUrl('/calendar');
        },
        error: (res) => this.showError(res)
      });
    });
  }

  setMode(mode: AccountMode): void {
    this.mode.set(mode);
    this.message.set('');
    this.error.set('');
  }

  register(): void {
    if (this.password !== this.repeatPassword) {
      this.message.set('');
      this.error.set('Passwords do not match');
      return;
    }

    this.accountService
      .register({
        firstName: this.firstName,
        surname: this.surname,
        username: this.username,
        email: this.email,
        password: this.password,
        repeatPassword: this.repeatPassword,
        favoriteColor: this.favoriteColor
      })
      .subscribe({
      next: (response) => {
        this.message.set(response.message);
        this.error.set('');
        this.mode.set('verify');
      },
      error: (response) => this.showError(response)
    });
  }

  verifyEmail(): void {
    this.accountService.verifyEmail(this.email, this.verificationCode).subscribe({
      next: (response) => {
        this.message.set(response.message);
        this.error.set('');
        this.mode.set('login');
      },
      error: (response) => this.showError(response)
    });
  }

  login(): void {
    this.accountService.login(this.username, this.password).subscribe({
      next: (response) => {
        this.loggedInUser.set(response.username);
        localStorage.setItem('sepUser', response.username);
        if (response.token) {
          localStorage.setItem('sepToken', response.token);
          this.webSocketService.connect();
        }
        this.message.set(response.message);
        this.error.set('');
        this.router.navigateByUrl('/calendar');
      },
      error: (response) => this.showError(response)
    });
  }

  logout(): void {
    this.webSocketService.disconnect();
    this.loggedInUser.set('');
    localStorage.removeItem('sepUser');
    localStorage.removeItem('sepToken');
    this.message.set('Logged out successfully');
  }

  forgotPassword(): void {
    this.accountService.forgotPassword(this.username, this.favoriteColor).subscribe({
      next: (response) => {
        this.message.set(response.message);
        this.error.set('');
        this.mode.set('reset');
      },
      error: (response) => this.showError(response)
    });
  }

  resetPassword(): void {
    if (this.newPassword !== this.repeatNewPassword) {
      this.message.set('');
      this.error.set('Passwords do not match');
      return;
    }

    this.accountService
      .resetPassword(this.username, this.favoriteColor, this.newPassword, this.repeatNewPassword)
      .subscribe({
      next: (response) => {
        this.message.set(response.message);
        this.error.set('');
        this.mode.set('login');
      },
      error: (response) => this.showError(response)
    });
  }

  sendResetCode(): void {
    this.accountService.sendResetCode(this.username).subscribe({
      next: (response) => {
        this.message.set(response.message);
        this.error.set('');
        this.mode.set('forgot-code');
      },
      error: (response) => this.showError(response)
    });
  }

  resetPasswordWithCode(): void {
    if (this.newPassword !== this.repeatNewPassword) {
      this.message.set('');
      this.error.set('Passwords do not match');
      return;
    }

    this.accountService
      .resetPasswordWithCode(this.username, this.resetCode, this.newPassword, this.repeatNewPassword)
      .subscribe({
        next: (response) => {
          this.message.set(response.message);
          this.error.set('');
          this.mode.set('login');
        },
        error: (response) => this.showError(response)
      });
  }

  private showError(response: { error?: { message?: string } }): void {
    this.message.set('');
    this.error.set(response.error?.message ?? 'Something went wrong');
  }
}
