import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface AuthResponse {
  message: string;
  username: string;
  token?: string;
}

export interface MessageResponse {
  message: string;
}

@Injectable({ providedIn: 'root' })
export class AccountManagementService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/account';

  register(account: {
    firstName: string;
    surname: string;
    username: string;
    email: string;
    password: string;
    repeatPassword: string;
    favoriteColor: string;
  }): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, account);
  }

  login(username: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, { username, password });
  }

  loginWithGoogle(idToken: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/oauth/google`, { idToken });
  }

  verifyEmail(email: string, code: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/verify-email`, { email, code });
  }

  forgotPassword(username: string, favoriteColor: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/forgot-password`, {
      username,
      favoriteColor
    });
  }

  resetPassword(
    username: string,
    favoriteColor: string,
    newPassword: string,
    repeatNewPassword: string
  ): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/reset-password`, {
      username,
      favoriteColor,
      newPassword,
      repeatNewPassword
    });
  }

  sendResetCode(username: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/send-reset-code`, { username });
  }

  resetPasswordWithCode(
    username: string,
    code: string,
    newPassword: string,
    repeatNewPassword: string
  ): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/reset-password-with-code`, {
      username,
      code,
      newPassword,
      repeatNewPassword
    });
  }
}
