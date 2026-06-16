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

  await expect(page.getByRole('heading', { name: 'Add expense' })).toBeVisible();
  await expect(page.getByText(/Amount/)).toBeVisible();
  await expect(page.getByText('Trip', { exact: true })).toBeVisible();
  await expect(page.getByText('Date', { exact: true })).toBeVisible();
  await expect(page.getByText('Category', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Add' })).toBeEnabled();
});

test('Eingabefelder werden angezeigt', async ({ page }) => {
  await page.goto('/cashbook');

  await expect(page.getByLabel(/Amount/)).toBeVisible();
  await expect(page.getByLabel('Trip')).toBeVisible();
  await expect(page.getByLabel('Date')).toBeVisible();
  await expect(page.getByLabel('Category')).toBeVisible();
});

test('Zurueck-Link navigiert zur Spending Overview', async ({ page }) => {
  await page.goto('/cashbook');
  await page.getByRole('link', { name: 'Back to overview' }).click();

  await expect(page).toHaveURL(/\/overview$/);
});
