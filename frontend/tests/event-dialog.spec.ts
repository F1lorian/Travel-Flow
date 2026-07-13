import { test, expect } from '@playwright/test';
import {
  authenticate,
  calendarDate,
  mockCalendarEvents,
  mockConflictEvents,
  mockEventParticipants,
  mockFriends,
  mockTrips,
} from './helpers';

function todayDateString(): string {
  return new Date().toISOString().split('T')[0];
}

function dayAfter(day: string): string {
  const date = new Date(`${day}T12:00:00`);
  date.setDate(date.getDate() + 1);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const dayOfMonth = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${dayOfMonth}`;
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
      title: 'Test Event',
      start: `${eventDate}T10:00:00`,
      end: `${eventDate}T11:00:00`,
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

  await expect(page.locator('.fc-event-title', { hasText: 'Test Event' })).toBeHidden();
});

test('Attending-friends-Liste zeigt nur akzeptierte Teilnehmer ohne den Nutzer selbst im Show-Dialog', async ({ page }) => {
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
  // The viewer themselves is not listed, mirroring the edit dialog.
  await expect(page.getByText('Play Wright')).toBeHidden();
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
  await expect(page.locator('strong', { hasText: 'Svetoslav Ivanov' })).toBeVisible();

  await page.getByRole('button', { name: 'Cancel' }).last().click();
  await expect(page.getByText('Remove friend?')).toBeHidden();
  await expect(page.getByText('Svetoslav Ivanov')).toBeVisible();

  await page.getByTitle('Remove friend').click();
  await page.getByRole('button', { name: 'Remove', exact: true }).click();

  await expect(page.locator('mat-dialog-content').getByText('Svetoslav Ivanov')).toBeHidden();
});

test('All-day Checkbox speichert Mitternacht bis Folgetag und rendert als Ganztages-Event', async ({
  page,
}) => {
  const eventDate = todayDateString();
  const exclusiveEnd = dayAfter(eventDate);

  await mockCalendarEvents(page);
  await mockConflictEvents(page);

  const createRequests: { start: string; end: string }[] = [];
  await page.route('**/api/event/create/manual**', async (route) => {
    createRequests.push(route.request().postDataJSON());
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: '42',
        name: 'Ganztages-Event',
        kosten: 0,
        start: `${eventDate}T00:00:00`,
        end: `${exclusiveEnd}T00:00:00`,
      }),
    });
  });

  await page.goto('/calendar');
  await page.locator(`[data-date="${eventDate}"]`).first().click();
  await page.getByPlaceholder('Enter the title').fill('Ganztages-Event');
  await page.getByRole('checkbox', { name: 'All-day' }).check();
  await page.getByRole('button', { name: 'Create event' }).click();

  // Rendered as an all-day block bar, not as a timed dot entry. Waiting for it also
  // guarantees the create request has been sent before its body is checked below.
  await expect(
    page.locator('.fc-daygrid-block-event', { hasText: 'Ganztages-Event' }),
  ).toBeVisible();

  // Stored as midnight -> next-day midnight (FullCalendar's native all-day shape).
  expect(createRequests[0].start).toBe(`${eventDate}T00:00`);
  expect(createRequests[0].end).toBe(`${exclusiveEnd}T00:00`);
});

test('All-day Event zeigt aktivierte Checkbox und inklusives Enddatum im Edit-Dialog', async ({
  page,
}) => {
  const eventDate = todayDateString();

  await mockCalendarEvents(page, [
    {
      id: '1',
      title: 'Ganztages-Event',
      start: `${eventDate}T00:00:00`,
      end: `${dayAfter(eventDate)}T00:00:00`,
      kosten: 0,
      creatorUsername: 'playwright',
    },
  ]);

  await page.goto('/calendar');
  await page.getByText('Ganztages-Event').click();
  await page.getByRole('button', { name: 'Edit' }).click();

  await expect(page.getByRole('checkbox', { name: 'All-day' })).toBeChecked();
  // All-day mode shows date-only inputs with the inclusive last day, not the stored exclusive end.
  await expect(page.locator('input[type="date"]').nth(1)).toHaveValue(eventDate);
});
