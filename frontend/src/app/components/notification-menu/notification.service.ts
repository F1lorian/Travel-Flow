import { Injectable, computed, signal } from '@angular/core';

/** A single in-app notification shown in the bell dropdown. */
export interface AppNotification {
  id: number;
  message: string;
  timestamp: string; // ISO string of when the action happened
  read: boolean; // false -> shown bold (not opened yet), true -> shown normal
}

const STORAGE_KEY = 'sepNotifications';

/** Oldest notifications are dropped past this count so localStorage cannot grow forever. */
const MAX_NOTIFICATIONS = 50;

/**
 * Holds the user's in-app notifications. Components add notifications when something happens
 * (e.g. a trip or event is created) and the bell menu reads them. Persisted to localStorage so
 * they survive a page reload.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly items = signal<AppNotification[]>(this.load());

  // Seeded with the current time so ids stay unique across page reloads; incremented per
  // notification so two added in the same millisecond never collide.
  private nextId = Date.now();

  /** All notifications, newest first. */
  readonly notifications = this.items.asReadonly();

  /** Number of notifications the user has not opened yet (drives the bell badge). */
  readonly unreadCount = computed(() => this.items().filter(n => !n.read).length);

  /** Record a new notification for the given message, timestamped now and marked unread. */
  add(message: string): void {
    const notification: AppNotification = {
      id: this.nextId++,
      message,
      timestamp: new Date().toISOString(),
      read: false
    };
    this.items.update(list => [notification, ...list].slice(0, MAX_NOTIFICATIONS));
    this.persist();
  }

  /** Remove all notifications (the "Clear all" action in the bell menu). */
  clearAll(): void {
    this.items.set([]);
    this.persist();
  }

  /** Mark every notification as read. Called when the bell menu is opened. */
  markAllRead(): void {
    if (this.unreadCount() === 0) return;
    this.items.update(list => list.map(n => ({ ...n, read: true })));
    this.persist();
  }

  private load(): AppNotification[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as AppNotification[]) : [];
    } catch {
      return [];
    }
  }

  private persist(): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(this.items()));
  }
}
