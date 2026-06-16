import { test, expect } from '@playwright/test';
import { authenticate, mockCalendarEvents, mockJson, mockTrips } from './helpers';

test.beforeEach(async ({ page }) => {
  await authenticate(page);
  await mockTrips(page);
  await mockCalendarEvents(page);
});

test('Alle Elemente werden angezeigt', async ({ page }) => {
  await page.goto('/calendar');

  await expect(page.getByRole('heading', { name: 'Kalender' })).toBeVisible();
  await expect(page.locator('.fc')).toBeVisible();
  await expect(page.locator('.fc-toolbar-title')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Previous month' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Next month' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'today' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'month', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'week', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'day', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'list', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Event hinzufügen' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Event importieren' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Events exportieren' })).toBeVisible();
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

  await page.goto('/calendar');
  await page.locator('[data-date]').first().click();
  await page.getByPlaceholder('Titel eingeben').fill('Mein Test Event');
  await page.getByRole('button', { name: 'Erstellen' }).click();

  await expect(page.getByText('Mein Test Event')).toBeVisible();
});
