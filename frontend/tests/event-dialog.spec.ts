import { test, expect } from '@playwright/test';
import { authenticate, mockCalendarEvents, mockFriends, mockTrips } from './helpers';

test.beforeEach(async ({ page }) => {
  await authenticate(page);
  await mockFriends(page);
  await mockTrips(page);
});

test('Alle Elemente werden angezeigt wenn mode = create', async ({ page }) => {
  await mockCalendarEvents(page);
  await page.goto('/calendar');
  await page.locator('[data-date]').first().click();

  await expect(page.getByRole('heading', { name: 'Create new Event' })).toBeVisible();
  await expect(page.getByPlaceholder('Enter the title')).toBeVisible();
  await expect(page.getByPlaceholder('Enter the costs')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Create event' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cancel' })).toBeVisible();
});

test('Alle Elemente werden angezeigt wenn mode = edit', async ({ page }) => {
  await mockCalendarEvents(page, [
    {
      id: '1',
      title: 'Test Event',
      start: '2026-06-15T10:00:00',
      end: '2026-06-15T11:00:00',
      kosten: 20,
    },
  ]);

  await page.goto('/calendar');
  await page.getByText('Test Event').click();
  await page.getByRole('button', { name: 'Edit' }).click();

  await expect(page.getByRole('heading', { name: 'Edit Event' })).toBeVisible();
  await expect(page.getByPlaceholder('Enter the title')).toHaveValue('Test Event');
  await expect(page.getByRole('button', { name: 'Save changes' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Delete' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cancel' })).toBeVisible();
});
