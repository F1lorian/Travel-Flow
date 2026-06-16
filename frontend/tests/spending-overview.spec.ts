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
  await expect(page.getByText('To')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Add expense' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Overview', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Recent expenses' })).toBeVisible();
});

test('Filter werden angezeigt', async ({ page }) => {
  await page.goto('/overview');

  await expect(page.locator('#trip-auswaehlen')).toBeVisible();
  await expect(page.getByLabel('Category')).toBeVisible();
});

test('Add-expense-Button navigiert zum Cashbook', async ({ page }) => {
  await page.goto('/overview');
  await page.getByRole('link', { name: 'Add expense' }).click();

  await expect(page).toHaveURL(/\/cashbook$/);
});
