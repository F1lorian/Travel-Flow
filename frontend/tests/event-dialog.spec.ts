import { test, expect } from '@playwright/test';
import { authenticate, mockCalendarEvents, mockTrips } from './helpers';

test.beforeEach(async ({ page }) => {
  await authenticate(page);
  await mockTrips(page);
});

test('Alle Elemente werden angezeigt wenn mode = create', async ({ page }) => {
  await mockCalendarEvents(page);
  await page.goto('/calendar');
  await page.locator('[data-date]').first().click();

  await expect(page.getByRole('heading', { name: 'Neues Event' })).toBeVisible();
  await expect(page.getByPlaceholder('Titel eingeben')).toBeVisible();
  await expect(page.getByPlaceholder('Kosten eingeben')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Erstellen' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Abbrechen' })).toBeVisible();
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
  await page.getByRole('button', { name: 'Event bearbeiten' }).click();

  await expect(page.getByRole('heading', { name: 'Event bearbeiten' })).toBeVisible();
  await expect(page.getByPlaceholder('Titel eingeben')).toHaveValue('Test Event');
  await expect(page.getByRole('button', { name: 'Änderungen speichern' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Löschen' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Abbrechen' })).toBeVisible();
});
