import { test, expect } from '@playwright/test';

test('Alle Elemente werden angezeigt wenn mode = create', async ({page}) => {

  await page.goto('http://localhost:4200');
  await page.locator('[data-date]').first().click();
  await expect(page.getByRole('heading', { name: 'Neues Event erstellen' })).toBeVisible();
  await expect(page.getByPlaceholder('Titel eingeben')).toBeVisible();
  await expect(page.getByRole('button', { name: "Erstellen" })).toBeVisible();
  await expect(page.getByRole('button', { name: "Abbrechen" })).toBeVisible();
  await page.getByRole('button', { name: "Abbrechen" }).click();
})

test('Alle Elemente werden angezeigt wenn mode = edit', async ({page}) => {

  await page.goto('http://localhost:4200');
  await page.locator('.fc-event').nth(0).click();
  await expect(page.getByRole('heading', { name: 'Event bearbeiten' })).toBeVisible();
  await expect(page.getByPlaceholder('Titel eingeben')).toBeVisible();
  await expect(page.getByRole('button', { name: "Änderungen speichern" })).toBeVisible();
  await expect(page.getByRole('button', { name: "Löschen" })).toBeVisible();
  await expect(page.getByRole('button', { name: "Abbrechen" })).toBeVisible();
  await page.getByRole('button', { name: "Abbrechen" }).click();
})
