import { Component, inject } from '@angular/core';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { NotificationService } from './notification.service';

/**
 * Bell icon in the app bar next to the profile menu. Shows a badge with the number of unread
 * notifications and lists them in a dropdown. Opening the dropdown marks them as read, so unread
 * items appear bold and read items appear normal.
 */
@Component({
  selector: 'app-notification-menu',
  standalone: true,
  imports: [MatBadgeModule, MatButtonModule, MatIconModule, MatMenuModule],
  templateUrl: './notification-menu.html',
  styleUrl: './notification-menu.css'
})
export class NotificationMenu {
  private readonly notificationService = inject(NotificationService);

  readonly notifications = this.notificationService.notifications;
  readonly unreadCount = this.notificationService.unreadCount;

  /** Once the menu is opened the user has seen the notifications, so mark them all read. */
  onMenuOpened(): void {
    this.notificationService.markAllRead();
  }

  /** Empties the notification list (the "Clear all" entry at the bottom of the menu). */
  clearAll(): void {
    this.notificationService.clearAll();
  }

  formatTime(timestamp: string): string {
    return new Date(timestamp).toLocaleString();
  }
}
