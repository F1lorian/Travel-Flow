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

const incomingTripInvites = [
  {
    tripId: 100,
    tripName: 'Rom-Trip',
    invitedByUserId: 4,
    invitedByUsername: 'florian',
    status: 'PENDING',
  },
];

const incomingEventInvites = [
  {
    eventId: 200,
    eventName: 'Konzert',
    invitedByUserId: 4,
    invitedByUsername: 'florian',
    status: 'PENDING',
  },
];

test.beforeEach(async ({ page }) => {
  await authenticate(page);

  // Friends page data
  await mockJson(page, '**/api/friends', friends);
  await mockJson(page, '**/api/friends/requests/incoming', []);
  await mockJson(page, '**/api/TripInvites/incoming', []);
  await mockJson(page, '**/api/event/invites/incoming', []);
});

test('Friends-Seite wird angezeigt', async ({ page }) => {
  await page.goto('/friends');

  await expect(page.getByRole('heading', { name: 'Friends', exact: true })).toBeVisible();
  await expect(page.getByText('Manage your friends, invitations and shared information.')).toBeVisible();

  await expect(page.getByRole('link', { name: 'Manage privacy settings' })).toBeVisible();

  await expect(page.getByRole('heading', { name: 'Search for users' })).toBeVisible();
  await expect(page.getByPlaceholder('Search by name or username')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Search' })).toBeVisible();

  await expect(page.getByRole('heading', { name: 'Friend requests' })).toBeVisible();
  await expect(page.getByText('No open friend requests.')).toBeVisible();

  await expect(page.getByRole('heading', { name: 'Your friends' })).toBeVisible();
  await expect(page.getByText('Svetoslav Ivanov')).toBeVisible();
  await expect(page.getByText('@sveto')).toBeVisible();

  await expect(page.getByRole('heading', { name: 'Incoming Invitations' })).toBeVisible();
  await expect(page.getByText('No open invitations.')).toBeVisible();

  await expect(page.getByRole('heading', { name: 'Shared information' })).toBeVisible();
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
  await expect(page.getByRole('button', { name: 'Request sent' })).toBeVisible();
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

//trip invites

test('Eingehende Trip-Einladung wird angezeigt', async ({ page }) => {
  await mockJson(page, '**/api/TripInvites/incoming', incomingTripInvites);

  await page.goto('/friends');

  await expect(page.getByRole('heading', { name: 'Incoming Invitations' })).toBeVisible();
  await expect(page.getByText('florian')).toBeVisible();
  await expect(page.getByText('Rom-Trip')).toBeVisible();
});

test('Keine offenen Einladungen zeigt Hinweistext', async ({ page }) => {
  await page.goto('/friends');

  await expect(page.getByText('No open invitations.')).toBeVisible();
});

test('Trip-Einladung annehmen entfernt sie aus der Liste', async ({ page }) => {
  await mockJson(page, '**/api/TripInvites/incoming', incomingTripInvites);
  await page.route('**/api/TripInvites/100/accept', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        tripId: 100,
        userId: 1,
        message: 'Trip invite accepted',
      }),
    });
  });

  await page.goto('/friends');
  await expect(page.getByText('Rom-Trip')).toBeVisible();

  await page.getByRole('button', { name: 'Accept' }).click();

  await expect(page.getByText('Rom-Trip')).toBeHidden();
  await expect(page.getByText('No open invitations.')).toBeVisible();
});

test('Trip-Einladung ablehnen entfernt sie aus der Liste', async ({ page }) => {
  await mockJson(page, '**/api/TripInvites/incoming', incomingTripInvites);
  await page.route('**/api/TripInvites/100/decline', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        tripId: 100,
        userId: 1,
        message: 'Trip invite declined',
      }),
    });
  });

  await page.goto('/friends');
  await expect(page.getByText('Rom-Trip')).toBeVisible();

  await page.getByRole('button', { name: 'Decline' }).click();

  await expect(page.getByText('Rom-Trip')).toBeHidden();
  await expect(page.getByText('No open invitations.')).toBeVisible();
});

//Event Invites

test('Eingehende Event-Einladung wird angezeigt', async ({ page }) => {
  await mockJson(page, '**/api/event/invites/incoming', incomingEventInvites);

  await page.goto('/friends');

  await expect(page.getByRole('heading', { name: 'Incoming Invitations' })).toBeVisible();
  await expect(page.getByText('florian')).toBeVisible();
  await expect(page.getByText('Konzert')).toBeVisible();
});

test('Event-Einladung annehmen entfernt sie aus der Liste', async ({ page }) => {
  await mockJson(page, '**/api/event/invites/incoming', incomingEventInvites);
  await page.route('**/api/event/200/respond?status=ACCEPTED', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });

  await page.goto('/friends');
  await expect(page.getByText('Konzert')).toBeVisible();

  await page.getByRole('button', { name: 'Accept' }).click();

  await expect(page.getByText('Konzert')).toBeHidden();
  await expect(page.getByText('No open invitations.')).toBeVisible();
});

test('Event-Einladung ablehnen entfernt sie aus der Liste', async ({ page }) => {
  await mockJson(page, '**/api/event/invites/incoming', incomingEventInvites);
  await page.route('**/api/event/200/respond?status=DECLINED', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });

  await page.goto('/friends');
  await expect(page.getByText('Konzert')).toBeVisible();

  await page.getByRole('button', { name: 'Decline' }).click();

  await expect(page.getByText('Konzert')).toBeHidden();
  await expect(page.getByText('No open invitations.')).toBeVisible();
});

test('Trip- und Event-Einladungen werden gleichzeitig angezeigt', async ({ page }) => {
  await mockJson(page, '**/api/TripInvites/incoming', incomingTripInvites);
  await mockJson(page, '**/api/event/invites/incoming', incomingEventInvites);

  await page.goto('/friends');

  await expect(page.getByText('Rom-Trip')).toBeVisible();
  await expect(page.getByText('Konzert')).toBeVisible();
});
