import { test, expect } from '@playwright/test';

test('Cashbook wird angezeigt', async ({ page }) => {
  await page.goto('http://localhost:4200/cashbook');

  await expect(page.getByRole('heading', { name: 'Cashbook' })).toBeVisible();
  await expect(page.getByText('Betrag')).toBeVisible();
  await expect(page.getByText('Trip:')).toBeVisible();
  await expect(page.getByText('Datum:')).toBeVisible();
  await expect(page.getByText('Kategorie:')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Hinzufuegen' })).toBeVisible();
});

test('Eingabefelder werden angezeigt', async ({ page }) => {
  await page.goto('http://localhost:4200/cashbook');

  await expect(page.locator('#betrag')).toBeVisible();
  await expect(page.locator('#trip')).toBeVisible();
  await expect(page.locator('#datum')).toBeVisible();
  await expect(page.locator('#kategorie')).toBeVisible();
});

test('Zurueck Link navigiert zur Spending Overview', async ({ page }) => {
  await page.goto('http://localhost:4200/cashbook');

  await page.getByRole('link', { name: 'ZurГјck zur Гњbersicht' }).click();

  await expect(page).toHaveURL(/\/overview$/);
});
