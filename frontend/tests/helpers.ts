import { Page } from '@playwright/test';

function padDatePart(value: number): string {
  return value.toString().padStart(2, '0');
}

export function calendarDate(day = 15): string {
  const now = new Date();
  return `${now.getFullYear()}-${padDatePart(now.getMonth() + 1)}-${padDatePart(day)}`;
}

export async function clearSession(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.removeItem('sepToken');
    localStorage.removeItem('sepUser');
  });

  if (page.url() !== 'about:blank') {
    await page.evaluate(() => {
      localStorage.removeItem('sepToken');
      localStorage.removeItem('sepUser');
    });
  }
}

export async function authenticate(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem('sepToken', 'playwright-test-token');
    localStorage.setItem('sepUser', 'playwright');
  });

  if (page.url() !== 'about:blank') {
    await page.evaluate(() => {
      localStorage.setItem('sepToken', 'playwright-test-token');
      localStorage.setItem('sepUser', 'playwright');
    });
  }
}

export async function mockJson(
  page: Page,
  url: string,
  body: unknown,
  method = 'GET',
): Promise<void> {
  await page.route(url, async (route) => {
    if (route.request().method() !== method) {
      await route.fallback();
      return;
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    });
  });
}

export async function mockTrips(page: Page, trips: unknown[] = []): Promise<void> {
  await mockJson(page, '**/api/trips', trips);
}

export async function mockFriends(page: Page, friends: unknown[] = []): Promise<void> {
  await mockJson(page, '**/api/friends', friends);
  await mockJson(page, '**/api/friends/search?*', []);
}

export async function mockCalendarEvents(page: Page, events: unknown[] = []): Promise<void> {
  await mockJson(page, '**/api/event/pull/all_events', events);
}

export async function mockEventParticipants(
  page: Page,
  participants: unknown[] = [],
): Promise<void> {
  await mockJson(page, '**/api/event/*/participants', participants);
}

export async function mockConflictEvents(page: Page, events: unknown[] = []): Promise<void> {
  await mockJson(page, '**/api/event/conflict?*', events);
}
