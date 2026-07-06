import { TestBed } from '@angular/core/testing';

import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;

  beforeEach(() => {
    localStorage.removeItem('sepNotifications');
    TestBed.configureTestingModule({});
    service = TestBed.inject(NotificationService);
  });

  afterEach(() => {
    localStorage.removeItem('sepNotifications');
  });

  it('starts empty with no unread notifications', () => {
    expect(service.notifications()).toEqual([]);
    expect(service.unreadCount()).toBe(0);
  });

  it('adds a notification as unread and newest first', () => {
    service.add('Trip "Paris" created');
    service.add('Event "Louvre" added');

    const items = service.notifications();
    expect(items.length).toBe(2);
    expect(items[0].message).toBe('Event "Louvre" added');
    expect(items[1].message).toBe('Trip "Paris" created');
    expect(items.every(n => !n.read)).toBeTrue();
    expect(service.unreadCount()).toBe(2);
  });

  it('assigns unique ids even when notifications are added in the same millisecond', () => {
    service.add('first');
    service.add('second');

    const [a, b] = service.notifications();
    expect(a.id).not.toBe(b.id);
  });

  it('marks every notification as read', () => {
    service.add('one');
    service.add('two');

    service.markAllRead();

    expect(service.unreadCount()).toBe(0);
    expect(service.notifications().every(n => n.read)).toBeTrue();
  });

  it('clears all notifications', () => {
    service.add('one');

    service.clearAll();

    expect(service.notifications()).toEqual([]);
    expect(localStorage.getItem('sepNotifications')).toBe('[]');
  });

  it('persists notifications to localStorage so they survive a reload', () => {
    service.add('Trip "Rome" created');

    const stored = JSON.parse(localStorage.getItem('sepNotifications') ?? '[]');
    expect(stored.length).toBe(1);
    expect(stored[0].message).toBe('Trip "Rome" created');
  });

  it('caps the stored list so it cannot grow forever', () => {
    for (let i = 0; i < 60; i++) {
      service.add(`notification ${i}`);
    }

    expect(service.notifications().length).toBe(50);
    // The newest notification is kept, the oldest ones are dropped.
    expect(service.notifications()[0].message).toBe('notification 59');
  });
});
