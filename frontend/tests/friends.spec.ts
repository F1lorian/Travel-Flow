import { test, expect } from '@playwright/test';
import { authenticate, mockJson } from './helpers';

const friends = [
  {
    id: 2,
    username: 'sveto',
    firstName: 'Svetoslav',
    surname: 'Ivanov',
  },
];

const searchResults = [
  {
    id: 3,
    username: 'bozhidar',
    firstName: 'Bozhidar',
    surname: 'Petrov',
    connected: false,
  },
];

test.beforeEach(async ({ page }) => {
  await authenticate(page);
  await mockJson(page, '**/api/friends', friends);
});

test('Friends-Seite wird angezeigt', async ({ page }) => {
  await page.goto('/friends');

  await expect(page.getByRole('heading', { name: 'Friends', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Search for users' })).toBeVisible();
  await expect(page.getByPlaceholder('Search by name or username')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Search' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Your friends' })).toBeVisible();
  await expect(page.getByText('Svetoslav Ivanov')).toBeVisible();
  await expect(page.getByText('@sveto')).toBeVisible();
});

test('Suchergebnisse sind vor der Suche nicht sichtbar', async ({ page }) => {
  await page.goto('/friends');

  await expect(page.getByRole('heading', { name: 'Results' })).toBeHidden();
  await expect(page.getByText('No users found.')).toBeHidden();
});

test('Suche zeigt Ergebnisse an', async ({ page }) => {
  await mockJson(page, '**/api/friends/search?name=boz', searchResults);

  await page.goto('/friends');
  await page.getByPlaceholder('Search by name or username').fill('boz');
  await page.getByRole('button', { name: 'Search' }).click();

  await expect(page.getByRole('heading', { name: 'Results' })).toBeVisible();
  await expect(page.getByText('Bozhidar Petrov')).toBeVisible();
  await expect(page.getByText('@bozhidar')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Add to friends' })).toBeVisible();
});

test('Leere Suche zeigt keine Ergebnisse an', async ({ page }) => {
  await mockJson(page, '**/api/friends/search?name=unknown', []);

  await page.goto('/friends');
  await page.getByPlaceholder('Search by name or username').fill('unknown');
  await page.getByRole('button', { name: 'Search' }).click();

  await expect(page.getByRole('heading', { name: 'Results' })).toBeVisible();
  await expect(page.getByText('No users found.')).toBeVisible();
});
