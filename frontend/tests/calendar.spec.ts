import { test, expect } from '@playwright/test';
import {
  authenticate,
  mockCalendarEvents,
  mockConflictEvents,
  mockFriends,
  mockJson,
  mockTrips,
} from './helpers';

function todayDateString(): string {
  return new Date().toISOString().split('T')[0];
}

function tomorrowDateString(): string {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  return tomorrow.toISOString().split('T')[0];
}

test.beforeEach(async ({ page }) => {
  await authenticate(page);
  await mockFriends(page);
  await mockTrips(page);
  await mockCalendarEvents(page);
});

test('Alle Elemente werden angezeigt', async ({ page }) => {
  await page.goto('/calendar');

  await expect(page.getByRole('heading', { name: 'Calendar' })).toBeVisible();
  await expect(page.locator('.fc')).toBeVisible();
  await expect(page.locator('.fc-toolbar-title')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Previous month' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Next month' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'today' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'month', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'week', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'day', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'list', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Add Event' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Import Event' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Export Event' })).toBeVisible();
});

test('Event wird über Dialog erstellt', async ({ page }) => {
  const eventDate = todayDateString();
  let events: unknown[] = [];

  await page.route('**/api/event/pull/all_events**', async (route) => {
    if (route.request().method() !== 'GET') {
      await route.fallback();
      return;
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(events),
    });
  });

  await page.route('**/api/event/create/manual**', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.fallback();
      return;
    }

    const createdEvent = {
      id: '42',
      title: 'Mein Test Event',
      name: 'Mein Test Event',
      start: `${eventDate}T10:00:00`,
      end: `${eventDate}T11:00:00`,
      startTime: `${eventDate}T10:00:00`,
      endTime: `${eventDate}T11:00:00`,
      kosten: 0,
    };

    events = [createdEvent];

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(createdEvent),
    });
  });

  await mockConflictEvents(page);

  await page.goto('/calendar');

  await page.locator(`[data-date="${eventDate}"]`).first().click();
  await page.getByPlaceholder('Enter the title').fill('Mein Test Event');

  const costInput = page.getByPlaceholder('Enter the costs');
  if (await costInput.isVisible()) {
    await costInput.fill('0');
  }

  await page.getByRole('button', { name: /Create/ }).click();

  await expect(page.getByText('Mein Test Event')).toBeVisible();
});

test('Event von Freund bekommt eigene CSS-Klasse', async ({ page }) => {
  const eventDate = todayDateString();

  await mockFriends(page, [{ id: 2, username: 'bob', firstName: 'Bob', surname: 'Friend' }]);

  await mockCalendarEvents(page, [
    {
      id: '1',
      title: 'Friend Event',
      name: 'Friend Event',
      start: `${eventDate}T10:00:00`,
      end: `${eventDate}T11:00:00`,
      startTime: `${eventDate}T10:00:00`,
      endTime: `${eventDate}T11:00:00`,
      kosten: 20,
      creatorId: 2,
    },
  ]);

  await page.goto('/calendar');

  await expect(page.getByText('Friend Event')).toBeVisible();
  await expect(page.locator('.fc-event.friend-calendar-event')).toContainText('Friend Event');
});

test('Eingeladener Trip bekommt eigene CSS-Klasse', async ({ page }) => {
  const startDate = todayDateString();
  const endDate = tomorrowDateString();

  await mockTrips(page, [
    {
      id: 7,
      name: 'Invited Trip',
      startDate,
      endDate,
      events: [],
      invitedByUserId: 2,
      invitedByUsername: 'bob',
      invited: true,
    },
  ]);

  await page.goto('/calendar');

  await expect(page.getByText('Invited Trip')).toBeVisible();
  await expect(page.locator('.fc-event.invited-trip-event')).toContainText('Invited Trip');
});
