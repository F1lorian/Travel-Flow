import { test, expect } from '@playwright/test';

test('Trip Spending Overview wird angezeigt', async ({ page }) => {
  await page.goto('http://localhost:4200/trip-spending-overview/1');

  await expect(page.getByRole('heading', { name: /spending overview/i })).toBeVisible();
  await expect(page.getByText('Budget')).toBeVisible();
  await expect(page.getByText('Ausgegeben')).toBeVisible();
  await expect(page.getByText('VerfГјgbar')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Ausgabe hinzufГјgen' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Ausgaben nach Kategorie' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Letzte Ausgaben' })).toBeVisible();
});

test('Filter werden angezeigt', async ({ page }) => {
  await page.goto('http://localhost:4200/trip-spending-overview/1');

  await expect(page.locator('#overview-auswaehlen')).toBeVisible();
  await expect(page.locator('#category-filter')).toBeVisible();
});

test('Add expense button navigates to cashbook', async ({ page }) => {
  await page.goto('http://localhost:4200/trip-spending-overview/1');

  await page.getByRole('link', { name: 'Ausgabe hinzufГјgen' }).click();

  await expect(page).toHaveURL(/\/cashbook$/);
});
