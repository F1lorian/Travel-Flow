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

const incomingRequests = [
  {
    id: 10,
    senderId: 4,
    senderUsername: 'florian',
    receiverId: 1,
    receiverUsername: 'max',
    status: 'PENDING',
  },
];

test.beforeEach(async ({ page }) => {
  await authenticate(page);
  await mockJson(page, '**/api/friends', friends);
  await mockJson(page, '**/api/friends/requests/incoming', []);
});

test('Friends-Seite wird angezeigt', async ({ page }) => {
  await page.goto('/friends');

  await expect(page.getByRole('heading', { name: 'Friends', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Search for users' })).toBeVisible();
  await expect(page.getByPlaceholder('Search by name or username')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Search' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Friend requests' })).toBeVisible();
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
  await expect(page.getByRole('button', { name: 'Send friend request' })).toBeVisible();
});

test('Leere Suche zeigt keine Ergebnisse an', async ({ page }) => {
  await mockJson(page, '**/api/friends/search?name=unknown', []);

  await page.goto('/friends');
  await page.getByPlaceholder('Search by name or username').fill('unknown');
  await page.getByRole('button', { name: 'Search' }).click();

  await expect(page.getByRole('heading', { name: 'Results' })).toBeVisible();
  await expect(page.getByText('No users found.')).toBeVisible();
});

test('Bereits verbundener Nutzer zeigt deaktivierten Button', async ({ page }) => {
  const connectedResult = [{ ...searchResults[0], connected: true }];
  await mockJson(page, '**/api/friends/search?name=boz', connectedResult);

  await page.goto('/friends');
  await page.getByPlaceholder('Search by name or username').fill('boz');
  await page.getByRole('button', { name: 'Search' }).click();

  const button = page.getByRole('button', { name: 'Already connected' });
  await expect(button).toBeVisible();
  await expect(button).toBeDisabled();
});

test('Freundschaftsanfrage senden aktualisiert die Suchergebnisse', async ({ page }) => {
  await mockJson(page, '**/api/friends/search?name=boz', searchResults);
  await page.route('**/api/friends/requests/3', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 20,
        senderId: 1,
        senderUsername: 'max',
        receiverId: 3,
        receiverUsername: 'bozhidar',
        status: 'PENDING',
      }),
    });
  });

  await page.goto('/friends');
  await page.getByPlaceholder('Search by name or username').fill('boz');
  await page.getByRole('button', { name: 'Search' }).click();
  await page.getByRole('button', { name: 'Send friend request' }).click();

  // Bei einer neuen PENDING-Anfrage bleibt der Nutzer als "nicht verbunden" markiert,
  // bis der Empfänger annimmt.
  await expect(page.getByRole('button', { name: 'Send friend request' })).toBeVisible();
});

test('Eingehende Freundschaftsanfrage wird angezeigt', async ({ page }) => {
  await mockJson(page, '**/api/friends/requests/incoming', incomingRequests);

  await page.goto('/friends');

  await expect(page.getByRole('heading', { name: 'Friend requests' })).toBeVisible();
  await expect(page.getByText('florian')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Accept' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Decline' })).toBeVisible();
});

test('Keine eingehenden Anfragen zeigt Hinweistext', async ({ page }) => {
  await page.goto('/friends');

  await expect(page.getByText('No open friend requests.')).toBeVisible();
});

test('Anfrage annehmen entfernt sie aus der Liste und aktualisiert Freunde', async ({ page }) => {
  await mockJson(page, '**/api/friends/requests/incoming', incomingRequests);
  await page.route('**/api/friends/requests/10/accept', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 10,
        senderId: 4,
        senderUsername: 'florian',
        receiverId: 1,
        receiverUsername: 'max',
        status: 'ACCEPTED',
      }),
    });
  });

  await page.goto('/friends');
  await expect(page.getByRole('button', { name: 'Accept' })).toBeVisible();

  await page.getByRole('button', { name: 'Accept' }).click();

  await expect(page.getByText('No open friend requests.')).toBeVisible();
});

test('Anfrage ablehnen entfernt sie aus der Liste', async ({ page }) => {
  await mockJson(page, '**/api/friends/requests/incoming', incomingRequests);
  await page.route('**/api/friends/requests/10/decline', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 10,
        senderId: 4,
        senderUsername: 'florian',
        receiverId: 1,
        receiverUsername: 'max',
        status: 'DECLINED',
      }),
    });
  });

  await page.goto('/friends');
  await expect(page.getByRole('button', { name: 'Decline' })).toBeVisible();

  await page.getByRole('button', { name: 'Decline' }).click();

  await expect(page.getByText('No open friend requests.')).toBeVisible();
});
