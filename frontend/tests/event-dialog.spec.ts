import { test, expect } from '@playwright/test';
import {
  authenticate,
  calendarDate,
  mockCalendarEvents,
  mockEventParticipants,
  mockFriends,
  mockTrips,
} from './helpers';

function todayDateString(): string {
  return new Date().toISOString().split('T')[0];
}

test.beforeEach(async ({ page }) => {
  await authenticate(page);
  await mockFriends(page);
  await mockTrips(page);
  await mockEventParticipants(page);
});

test('Alle Elemente werden angezeigt wenn mode = create', async ({ page }) => {
  await mockCalendarEvents(page);
  await page.goto('/calendar');

  const eventDate = todayDateString();
  await page.locator(`[data-date="${eventDate}"]`).first().click();

  await expect(page.getByRole('heading', { name: 'Create new Event' })).toBeVisible();
  await expect(page.getByPlaceholder('Enter the title')).toBeVisible();
  await expect(page.getByPlaceholder('Enter the costs')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Create event' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cancel' })).toBeVisible();
});

test('Alle Elemente werden angezeigt wenn mode = edit (Owner)', async ({ page }) => {
  const eventDate = todayDateString();

  await mockCalendarEvents(page, [
    {
      id: '1',
      name: 'Test Event',
      startTime: `${eventDate}T10:00:00`,
      endTime: `${eventDate}T11:00:00`,
      kosten: 20,
      creatorUsername: 'playwright',
    },
  ]);

  await page.goto('/calendar');
  await page.getByText('Test Event').click();
  await page.getByRole('button', { name: 'Edit' }).click();

  await expect(page.getByRole('heading', { name: 'Edit Event' })).toBeVisible();
  await expect(page.getByPlaceholder('Enter the title')).toHaveValue('Test Event');
  await expect(page.getByRole('button', { name: 'Save changes' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Delete' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cancel' })).toBeVisible();
});

test('Show-Dialog zeigt Edit/Delete für Owner', async ({ page }) => {
  const eventDate = calendarDate();

  await mockCalendarEvents(page, [
    {
      id: '1',
      title: 'Test Event',
      start: `${eventDate}T10:00:00`,
      end: `${eventDate}T11:00:00`,
      kosten: 20,
      creatorUsername: 'playwright',
    },
  ]);

  await page.goto('/calendar');
  await page.getByText('Test Event').click();

  await expect(page.getByRole('heading', { name: 'Test Event' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Edit' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Delete' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Leave Event' })).toBeHidden();
});

test('Show-Dialog zeigt Leave Event für Nicht-Owner', async ({ page }) => {
  const eventDate = calendarDate();

  await mockCalendarEvents(page, [
    {
      id: '1',
      title: 'Fremdes Event',
      start: `${eventDate}T10:00:00`,
      end: `${eventDate}T11:00:00`,
      kosten: 20,
      creatorUsername: 'anderer-user',
    },
  ]);

  await page.goto('/calendar');
  await page.getByText('Fremdes Event').click();

  await expect(page.getByRole('heading', { name: 'Fremdes Event' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Edit' })).toBeHidden();
  await expect(page.getByRole('button', { name: 'Delete' })).toBeHidden();
  await expect(page.getByRole('button', { name: 'Leave Event' })).toBeVisible();
});

test('Klick auf Delete im Show-Dialog entfernt das Event ohne Reload', async ({ page }) => {
  const eventDate = calendarDate();

  await mockCalendarEvents(page, [
    {
      id: '1',
      title: 'Test Event',
      start: `${eventDate}T10:00:00`,
      end: `${eventDate}T11:00:00`,
      kosten: 20,
      creatorUsername: 'playwright',
    },
  ]);
  await page.route('**/api/event/delete', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });

  await page.goto('/calendar');
  await page.getByText('Test Event').click();
  await page.getByRole('button', { name: 'Delete' }).click();

  await expect(page.getByText('Test Event')).toBeHidden();
});

test('Attending-friends-Liste zeigt nur akzeptierte Teilnehmer im Show-Dialog', async ({ page }) => {
  const eventDate = calendarDate();

  await mockCalendarEvents(page, [
    {
      id: '1',
      title: 'Test Event',
      start: `${eventDate}T10:00:00`,
      end: `${eventDate}T11:00:00`,
      kosten: 20,
      creatorUsername: 'playwright',
    },
  ]);
  await mockEventParticipants(page, [
    { userId: 1, username: 'playwright', firstname: 'Play', surname: 'Wright', status: 'ACCEPTED' },
    { userId: 2, username: 'sveto', firstname: 'Svetoslav', surname: 'Ivanov', status: 'ACCEPTED' },
    { userId: 3, username: 'florian', firstname: 'Florian', surname: 'Muster', status: 'PENDING' },
  ]);

  await page.goto('/calendar');
  await page.getByText('Test Event').click();

  await expect(page.getByText('Svetoslav Ivanov')).toBeVisible();
  await expect(page.getByText('Florian Muster')).toBeHidden();
});

test('Delete-Participant-Button nur im Edit-Dialog für Owner sichtbar', async ({ page }) => {
  const eventDate = calendarDate();

  await mockCalendarEvents(page, [
    {
      id: '1',
      title: 'Test Event',
      start: `${eventDate}T10:00:00`,
      end: `${eventDate}T11:00:00`,
      kosten: 20,
      creatorUsername: 'playwright',
    },
  ]);
  await mockEventParticipants(page, [
    { userId: 2, username: 'sveto', firstname: 'Svetoslav', surname: 'Ivanov', status: 'ACCEPTED' },
  ]);

  await page.goto('/calendar');
  await page.getByText('Test Event').click();
  await page.getByRole('button', { name: 'Edit' }).click();

  await expect(page.getByText('Svetoslav Ivanov')).toBeVisible();
  await expect(page.getByTitle('Remove friend')).toBeVisible();
});

test('Entfernen eines Teilnehmers erfordert Bestätigung', async ({ page }) => {
  const eventDate = calendarDate();

  await mockCalendarEvents(page, [
    {
      id: '1',
      title: 'Test Event',
      start: `${eventDate}T10:00:00`,
      end: `${eventDate}T11:00:00`,
      kosten: 20,
      creatorUsername: 'playwright',
    },
  ]);
  await mockEventParticipants(page, [
    { userId: 2, username: 'sveto', firstname: 'Svetoslav', surname: 'Ivanov', status: 'ACCEPTED' },
  ]);
  await page.route('**/api/event/1/participants/2', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });

  await page.goto('/calendar');
  await page.getByText('Test Event').click();
  await page.getByRole('button', { name: 'Edit' }).click();
  await page.getByTitle('Remove friend').click();

  await expect(page.getByText('Remove friend?')).toBeVisible();
  await expect(page.getByText('Svetoslav Ivanov', { exact: false })).toBeVisible();

  await page.getByRole('button', { name: 'Cancel' }).last().click();
  await expect(page.getByText('Remove friend?')).toBeHidden();
  await expect(page.getByText('Svetoslav Ivanov')).toBeVisible();

  await page.getByTitle('Remove friend').click();
  await page.getByRole('button', { name: 'Remove' }).click();

  await expect(page.getByText('Svetoslav Ivanov')).toBeHidden();
});
