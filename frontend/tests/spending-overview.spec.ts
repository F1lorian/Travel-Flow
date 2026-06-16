import { test, expect } from '@playwright/test';
import { authenticate, mockJson, mockTrips } from './helpers';

test.beforeEach(async ({ page }) => {
  await authenticate(page);
  await mockTrips(page, [{ id: 1, name: 'Berlin', events: [] }]);
  await mockJson(page, '**/api/ausgabenliste?*', []);
  await mockJson(page, '**/api/proKategorie?*', []);
});

test('Spending Overview wird angezeigt', async ({ page }) => {
  await page.goto('/overview');

  await expect(page.getByRole('heading', { name: 'Spending Overview' })).toBeVisible();
  await expect(page.getByText('From')).toBeVisible();
  await expect(page.getByText('Bis')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Ausgabe hinzufügen' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Übersicht' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Letzte Ausgaben' })).toBeVisible();
});

test('Filter werden angezeigt', async ({ page }) => {
  await page.goto('/overview');

  await expect(page.locator('#trip-auswaehlen')).toBeVisible();
  await expect(page.getByLabel('Kategorie')).toBeVisible();
});

test('Add-expense-Button navigiert zum Cashbook', async ({ page }) => {
  await page.goto('/overview');
  await page.getByRole('link', { name: 'Ausgabe hinzufügen' }).click();

  await expect(page).toHaveURL(/\/cashbook$/);
});
