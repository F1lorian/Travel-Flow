import { test, expect } from '@playwright/test';

test('Alle Elemente werden angezeigt', async ({page}) => {

  await page.goto('http://localhost:4200');
  await expect(page).toHaveTitle('Sep');
  await expect(page.getByRole('heading', { name: 'Lovelace Babbage' })).toBeVisible();
  await page.waitForSelector('.fc');
  await expect(page.locator('.fc')).toBeVisible();

})

test('event wird über dialog erstellt', async ({ page }) => {

  await page.goto('http://localhost:4200');
  await page.locator('[data-date]').first().click();
  await page.locator('input').fill('Mein Test Event');
  await page.getByText('Speichern').click();
  await expect(page.getByText('Mein Test Event')).toBeVisible();
});

test('Event wird über dialog geändert', async ({page}) => {
  await page.goto('http://localhost:4200');
  await page.locator('.fc-event').nth(0).click();
  await expect(page.getByPlaceholder('Titel eingeben')).toBeVisible();
  await page.getByPlaceholder('Titel eingeben').fill('test change');
  await page.getByText('Speichern').click();
  await expect(page.getByText('testchange')).toBeVisible();

  //noch nicht completed


});

test('Event wird über dialog gelöscht', async ({page}) => {
  //noch nicht completed
});
