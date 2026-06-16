import { test, expect } from '@playwright/test';
import { authenticate, mockJson, mockTrips } from './helpers';

test.beforeEach(async ({ page }) => {
  await authenticate(page);
  await mockTrips(page, [{ id: 1, name: 'Berlin', events: [] }]);
  await mockJson(page, '**/api/ausgabenliste?*', []);
  await mockJson(page, '**/api/proKategorie?*', []);
});

test('Cashbook wird angezeigt', async ({ page }) => {
  await page.goto('/cashbook');

  await expect(page.getByRole('heading', { name: 'Ausgabe hinzufügen' })).toBeVisible();
  await expect(page.getByText('Betrag (€)')).toBeVisible();
  await expect(page.getByText('Trip', { exact: true })).toBeVisible();
  await expect(page.getByText('Datum', { exact: true })).toBeVisible();
  await expect(page.getByText('Kategorie', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Hinzufügen' })).toBeEnabled();
});

test('Eingabefelder werden angezeigt', async ({ page }) => {
  await page.goto('/cashbook');

  await expect(page.getByLabel('Betrag (€)')).toBeVisible();
  await expect(page.getByLabel('Trip')).toBeVisible();
  await expect(page.getByLabel('Datum')).toBeVisible();
  await expect(page.getByLabel('Kategorie')).toBeVisible();
});

test('Zurück-Link navigiert zur Spending Overview', async ({ page }) => {
  await page.goto('/cashbook');
  await page.getByRole('link', { name: 'Zurück zur Übersicht' }).click();

  await expect(page).toHaveURL(/\/overview$/);
});
