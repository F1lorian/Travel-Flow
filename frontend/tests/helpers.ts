import { Page } from '@playwright/test';

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

export async function mockCalendarEvents(page: Page, events: unknown[] = []): Promise<void> {
  await mockJson(page, '**/api/event/pull/all_events', events);
}
