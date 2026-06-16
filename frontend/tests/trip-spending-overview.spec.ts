import { test, expect } from '@playwright/test';
import { authenticate, mockJson, mockTrips } from './helpers';

test.beforeEach(async ({ page }) => {
  await authenticate(page);
  await mockTrips(page, [{ id: 1, name: 'Berlin', events: [] }]);
  await mockJson(page, '**/api/trips/1', { id: 1, name: 'Berlin', events: [] });
  await mockJson(page, '**/api/budget?*', 1000);
  await mockJson(page, '**/api/warningRange?*', 100);
  await mockJson(page, '**/api/gesamtausgabenTrip?*', 250);
  await mockJson(page, '**/api/verfuegbar?*', 750);
  await mockJson(page, '**/api/ausgabenlisteTrip?*', []);
  await mockJson(page, '**/api/proKategorieTrip?*', []);
});

test('Trip Spending Overview wird angezeigt', async ({ page }) => {
  await page.goto('/trip-spending-overview/1');

  await expect(page.getByRole('heading', { name: 'Berlin - Spending Overview' })).toBeVisible();
  await expect(page.getByText('Budget', { exact: true })).toBeVisible();
  await expect(page.getByText('Spent', { exact: true })).toBeVisible();
  await expect(page.getByText('Available', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Add expense' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Expenses by category' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Recent expenses' })).toBeVisible();
});

test('Filter werden angezeigt', async ({ page }) => {
  await page.goto('/trip-spending-overview/1');

  await expect(page.getByLabel('Trip')).toBeVisible();
  await expect(page.getByLabel('Category')).toBeVisible();
});

test('Add-expense-Button navigiert zum Cashbook', async ({ page }) => {
  await page.goto('/trip-spending-overview/1');
  await page.getByRole('link', { name: 'Add expense' }).click();

  await expect(page).toHaveURL(/\/cashbook$/);
});
