import { test, expect } from '@playwright/test';

test('Alle Elemente werden angezeigt', async ({page}) => {
  await page.goto('http://localhost:4200');
  await page.waitForSelector('.fc');
  await expect(page.locator('.fc')).toBeVisible();
  await expect(page.locator('.fc-toolbar-title')).toBeVisible();
  await expect(page.locator('.fc-prev-button')).toBeVisible();
  await expect(page.locator('.fc-next-button')).toBeVisible();
  await expect(page.locator('.fc-today-button')).toBeVisible();
  await expect(page.locator('.fc-dayGridMonth-button')).toBeVisible();
  await expect(page.locator('.fc-timeGridWeek-button')).toBeVisible();
  await expect(page.locator('.fc-timeGridDay-button')).toBeVisible();
  await expect(page.locator('.fc-listYear-button')).toBeVisible();
  await expect(page.locator('.fc-eventAddenButton-button')).toBeVisible();
  await expect(page.locator('.fc-eventImportButton-button')).toBeVisible();


})

test('event wird über dialog erstellt', async ({ page }) => {
  await page.goto('http://localhost:4200');
  await page.locator('[data-date]').first().click();
  await page.locator('input').fill('Mein Test Event');
  await page.getByRole('button', {name: 'Erstellen'}).click();
  await expect(page.getByText('Mein Test Event')).toBeVisible();
});

test('Event wird über dialog geändert', async ({page}) => {

});

test('Event wird über dialog gelöscht', async ({page}) => {

});
