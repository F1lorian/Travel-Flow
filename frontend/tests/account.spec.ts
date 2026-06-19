import { test, expect } from '@playwright/test';
import { clearSession, mockCalendarEvents, mockJson, mockTrips } from './helpers';

test.beforeEach(async ({ page }) => {
  await clearSession(page);
});

test('Startscreen wird fuer nicht eingeloggte Nutzer angezeigt', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveURL(/\/account$/);
  await expect(page.getByRole('heading', { name: 'Log in to SEP' })).toBeVisible();
  await expect(page.getByPlaceholder('User name or email')).toBeVisible();
  await expect(page.getByPlaceholder('Password')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Log in' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Create new account' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Calendar' })).toBeHidden();
});

test('Geschuetzte Seiten leiten ohne Login zum Startscreen', async ({ page }) => {
  await page.goto('/calendar');

  await expect(page).toHaveURL(/\/account$/);
  await expect(page.getByRole('heading', { name: 'Log in to SEP' })).toBeVisible();
});

test('Login fuehrt vom Startscreen zum Kalender', async ({ page }) => {
  await mockJson(
    page,
    '**/api/account/login',
    {
      message: 'Logged in successfully',
      username: 'playwright',
      token: 'playwright-test-token',
    },
    'POST',
  );
  await mockTrips(page);
  await mockCalendarEvents(page);

  await page.goto('/');
  await page.getByPlaceholder('User name or email').fill('playwright');
  await page.getByPlaceholder('Password').fill('Secret123!');
  await page.getByRole('button', { name: 'Log in' }).click();

  await expect(page).toHaveURL(/\/calendar$/);
  await expect(page.getByRole('heading', { name: 'Calendar' })).toBeVisible();
});
