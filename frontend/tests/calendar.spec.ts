import { test, expect } from '@playwright/test';
import {
  authenticate,
  mockCalendarEvents,
  mockConflictEvents,
  mockFriends,
  mockJson,
  mockTrips,
} from './helpers';

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
  await mockJson(
    page,
    '**/api/event/create/manual',
    {
      id: '42',
      name: 'Mein Test Event',
      kosten: 0,
      start: '2026-06-15T00:00:00',
      end: '2026-06-15T01:00:00',
    },
    'POST',
  );
  await mockConflictEvents(page);

  await page.goto('/calendar');
  await page.locator('[data-date]').first().click();
  await page.getByPlaceholder('Enter the title').fill('Mein Test Event');
  await page.getByRole('button', { name: 'Create' }).click();

  await expect(page.getByText('Mein Test Event')).toBeVisible();
});

test('Event von Freund bekommt eigene CSS-Klasse', async ({ page }) => {
  await mockFriends(page, [{ id: 2, username: 'bob', firstName: 'Bob', surname: 'Friend' }]);
  await mockCalendarEvents(page, [
    {
      id: '1',
      title: 'Friend Event',
      start: '2026-06-15T10:00:00',
      end: '2026-06-15T11:00:00',
      kosten: 20,
      creatorId: 2,
    },
  ]);

  await page.goto('/calendar');

  await expect(page.locator('.fc-event.friend-calendar-event')).toContainText('Friend Event');
});

test('Eingeladener Trip bekommt eigene CSS-Klasse', async ({ page }) => {
  await mockTrips(page, [
    {
      id: 7,
      name: 'Invited Trip',
      startDate: '2026-06-15',
      endDate: '2026-06-16',
      events: [],
      invitedByUserId: 2,
      invitedByUsername: 'bob',
      invited: true,
    },
  ]);

  await page.goto('/calendar');

  await expect(page.locator('.fc-event.invited-trip-event')).toContainText('Invited Trip');
});
