import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AccountManagementService } from './account-management.service';

type AccountMode = 'login' | 'register' | 'forgot' | 'reset';

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

  mode = signal<AccountMode>('login');
  message = signal('');
  error = signal('');
  loggedInUser = signal(localStorage.getItem('sepUser') ?? '');

  firstName = '';
  surname = '';
  username = '';
  password = '';
  repeatPassword = '';
  favoriteColor = '';
  newPassword = '';
  repeatNewPassword = '';

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
        password: this.password,
        repeatPassword: this.repeatPassword,
        favoriteColor: this.favoriteColor
      })
      .subscribe({
      next: (response) => {
        this.loggedInUser.set(response.username);
        localStorage.setItem('sepUser', response.username);
        this.message.set(response.message);
        this.error.set('');
        this.router.navigateByUrl('/calendar');
      },
      error: (response) => this.showError(response)
    });
  }

  login(): void {
    this.accountService.login(this.username, this.password).subscribe({
      next: (response) => {
        this.loggedInUser.set(response.username);
        localStorage.setItem('sepUser', response.username);
        this.message.set(response.message);
        this.error.set('');
        this.router.navigateByUrl('/calendar');
      },
      error: (response) => this.showError(response)
    });
  }

  logout(): void {
    this.loggedInUser.set('');
    localStorage.removeItem('sepUser');
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

  private showError(response: { error?: { message?: string } }): void {
    this.message.set('');
    this.error.set(response.error?.message ?? 'Something went wrong');
  }
}
