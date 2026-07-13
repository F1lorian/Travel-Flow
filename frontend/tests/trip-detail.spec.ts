import { test, expect } from '@playwright/test';
import { authenticate, mockJson } from './helpers';

const trip = {
  id: 1,
  name: 'Rom-Trip',
  startDate: '2026-08-01',
  endDate: '2026-08-07',
  events: [],
  invitedByUserId: 0,
  invited: false,
  ownerUsername: 'playwright',
};

const foreignTrip = { ...trip, ownerUsername: 'anderer-user' };

const participants = [
  { userId: 1, username: 'playwright', firstname: 'Play', surname: 'Wright', status: 'ACCEPTED' },
  { userId: 2, username: 'sveto', firstname: 'Svetoslav', surname: 'Ivanov', status: 'ACCEPTED' },
  { userId: 3, username: 'florian', firstname: 'Florian', surname: 'Muster', status: 'PENDING' },
];

test.beforeEach(async ({ page }) => {
  await authenticate(page);
});

test('Owner sieht Invite- und Delete-Trip-Buttons', async ({ page }) => {
  await mockJson(page, '**/api/trips/1', trip);
  await mockJson(page, '**/api/trips/1/participants', participants);

  await page.goto('/trips/1');

  await expect(page.getByRole('heading', { name: 'Rom-Trip' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Invite Friends' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Delete Trip' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Leave Trip' })).toBeHidden();
});

test('Nicht-Owner sieht Leave-Trip-Button statt Invite/Delete', async ({ page }) => {
  await mockJson(page, '**/api/trips/1', foreignTrip);
  await mockJson(page, '**/api/trips/1/participants', participants);

  await page.goto('/trips/1');

  await expect(page.getByRole('heading', { name: 'Rom-Trip' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Invite Friends' })).toBeHidden();
  await expect(page.getByRole('button', { name: 'Delete Trip' })).toBeHidden();
  await expect(page.getByRole('button', { name: 'Leave Trip' })).toBeVisible();
});

test('Teilnehmerliste zeigt nur akzeptierte Mitglieder', async ({ page }) => {
  await mockJson(page, '**/api/trips/1', trip);
  await mockJson(page, '**/api/trips/1/participants', participants);

  await page.goto('/trips/1');

  await expect(page.getByText('Svetoslav Ivanov')).toBeVisible();
  await expect(page.getByText('Florian Muster')).toBeHidden();
});

test('Owner kann Teilnehmer entfernen (mit Bestätigung)', async ({ page }) => {
  await mockJson(page, '**/api/trips/1', trip);
  await mockJson(page, '**/api/trips/1/participants', participants);
  await page.route('**/api/trips/1/friends/2', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });

  await page.goto('/trips/1');

  const svetoRow = page.locator('li', { hasText: 'Svetoslav Ivanov' });
  await svetoRow.getByTitle('Remove participant').click();

  await expect(page.getByText('Remove friend?')).toBeVisible();
  await page.getByRole('button', { name: 'Remove', exact: true }).click();

  await expect(page.locator('.participant-list').getByText('Svetoslav Ivanov')).toBeHidden();
});

test('Trip-Header zeigt Gesamtkosten und All-day-Events werden markiert', async ({ page }) => {
  const tripWithEvents = {
    ...trip,
    events: [
      {
        id: 10,
        name: 'Kolosseum',
        kosten: 25,
        startTime: '2026-08-02T10:00:00',
        endTime: '2026-08-02T12:00:00',
      },
      {
        id: 11,
        name: 'Strandtag',
        kosten: 10.5,
        startTime: '2026-08-03T00:00:00',
        endTime: '2026-08-04T00:00:00',
      },
    ],
  };
  await mockJson(page, '**/api/trips/1', tripWithEvents);
  await mockJson(page, '**/api/trips/1/participants', participants);

  await page.goto('/trips/1');

  await expect(page.getByText(/Total costs: 35[.,]5 €/)).toBeVisible();
  await expect(page.locator('li', { hasText: 'Strandtag' }).getByText('All day')).toBeVisible();
  await expect(page.locator('li', { hasText: 'Kolosseum' }).getByText('All day')).toBeHidden();
});

test('Delete Trip zeigt Bestätigungsmodal mit allen Events', async ({ page }) => {
  const tripWithEvents = {
    ...trip,
    events: [
      { id: 10, name: 'Kolosseum', kosten: 25, startTime: '2026-08-02T10:00:00' },
    ],
  };
  await mockJson(page, '**/api/trips/1', tripWithEvents);
  await mockJson(page, '**/api/trips/1/participants', participants);

  await page.goto('/trips/1');
  await page.getByRole('button', { name: 'Delete Trip' }).click();

  await expect(page.getByText('Are you sure you want to delete this trip?')).toBeVisible();
  await expect(page.locator('.modal').getByText('Kolosseum')).toBeVisible();
});

test('Bestätigtes Delete Trip navigiert zurück zur Trip-Übersicht', async ({ page }) => {
  await mockJson(page, '**/api/trips/1', trip);
  await mockJson(page, '**/api/trips/1/participants', participants);
  await mockJson(page, '**/api/trips', []);
  await page.route('**/api/trips/1', async (route) => {
    if (route.request().method() === 'DELETE') {
      await route.fulfill({ status: 204 });
      return;
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(trip) });
  });

  await page.goto('/trips/1');
  await page.getByRole('button', { name: 'Delete Trip' }).click();
  await page.locator('.modal').getByRole('button', { name: 'Delete Trip' }).click();

  await expect(page).toHaveURL(/\/trips$/);
});

test('Leave Trip öffnet Bestätigung für sich selbst', async ({ page }) => {
  await mockJson(page, '**/api/trips/1', foreignTrip);
  await mockJson(page, '**/api/trips/1/participants', participants);
  await page.route('**/api/trips/1/friends/1', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });

  await page.goto('/trips/1');
  await page.getByRole('button', { name: 'Leave Trip' }).click();

  await expect(page.getByText('Leave Trip?')).toBeVisible();
  await page.getByRole('button', { name: 'Leave', exact: true }).click();

  await expect(page.getByText('Play Wright')).toBeHidden();
});
