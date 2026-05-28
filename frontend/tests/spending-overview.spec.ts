import { test, expect } from '@playwright/test';

test('Spending Overview wird angezeigt', async ({ page }) => {
  await page.goto('http://localhost:4200/overview');

  await expect(page.getByRole('heading', { name: 'Spending Overview' })).toBeVisible();
  await expect(page.getByText('From:')).toBeVisible();
  await expect(page.getByText('Bis:')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Ausgabe hinzufügen' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Übersicht' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Letzte ausgaben' })).toBeVisible();
});

test('Filter werden angezeigt', async ({ page }) => {
  await page.goto('http://localhost:4200/overview');

  await expect(page.locator('#trip-auswaehlen')).toBeVisible();
  await expect(page.locator('#category-filter')).toBeVisible();
});

test('Add expense button navigates to cashbook', async ({ page }) => {
  await page.goto('http://localhost:4200/overview');

  await page.getByRole('link', { name: 'Ausgabe hinzufГјgen' }).click();

  await expect(page).toHaveURL(/\/cashbook$/);
});
