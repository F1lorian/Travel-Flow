import { test, expect } from '@playwright/test';
import {
  authenticate,
  mockCalendarEvents,
  mockEventParticipants,
  mockFriends,
  mockTrips,
} from './helpers';

function todayDateString(): string {
  return new Date().toISOString().split('T')[0];
}

test.beforeEach(async ({ page }) => {
  await authenticate(page);
  await mockFriends(page);
  await mockTrips(page);
  await mockEventParticipants(page);
});

test('Alle Elemente werden angezeigt wenn mode = create', async ({ page }) => {
  await mockCalendarEvents(page);
  await page.goto('/calendar');

  const eventDate = todayDateString();
  await page.locator(`[data-date="${eventDate}"]`).first().click();

  await expect(page.getByRole('heading', { name: 'Create new Event' })).toBeVisible();
  await expect(page.getByPlaceholder('Enter the title')).toBeVisible();
  await expect(page.getByPlaceholder('Enter the costs')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Create event' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cancel' })).toBeVisible();
});

test('Alle Elemente werden angezeigt wenn mode = edit', async ({ page }) => {
  const eventDate = todayDateString();

  await mockCalendarEvents(page, [
    {
      id: '1',
      title: 'Test Event',
      name: 'Test Event',
      start: `${eventDate}T10:00:00`,
      end: `${eventDate}T11:00:00`,
      startTime: `${eventDate}T10:00:00`,
      endTime: `${eventDate}T11:00:00`,
      kosten: 20,
    },
  ]);

  await page.goto('/calendar');

  await expect(page.getByText('Test Event')).toBeVisible();

  await page.getByText('Test Event').click();
  await page.getByRole('button', { name: 'Edit' }).click();

  await expect(page.getByRole('heading', { name: 'Edit Event' })).toBeVisible();
  await expect(page.getByPlaceholder('Enter the title')).toHaveValue('Test Event');
  await expect(page.getByRole('button', { name: 'Save changes' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Delete' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cancel' })).toBeVisible();
});
