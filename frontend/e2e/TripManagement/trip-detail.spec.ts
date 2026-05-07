import { test, expect } from '@playwright/test';
import { mockGetTrip, mockAddEvent } from './helpers';

test.describe('Trip Detail page', () => {
  test('shows the trip name as heading', async ({ page }) => {
    await mockGetTrip(page, { id: 1, name: 'Paris Vacation', events: [] });
    await page.goto('/trips/1');

    await expect(page.getByRole('heading', { name: 'Paris Vacation' })).toBeVisible();
  });

  test('shows empty-state when trip has no events', async ({ page }) => {
    await mockGetTrip(page, { id: 1, name: 'Paris Vacation', events: [] });
    await page.goto('/trips/1');

    await expect(page.getByText('No events yet. Add one above!')).toBeVisible();
  });

  test('renders existing events', async ({ page }) => {
    await mockGetTrip(page, {
      id: 1,
      name: 'Paris Vacation',
      events: [
        { id: 10, name: 'Eiffel Tower', dateTime: '2026-06-01T14:30:00', kosten: 25 },
        { id: 11, name: 'Louvre', dateTime: '2026-06-02T10:00:00', kosten: 17 },
      ],
    });
    await page.goto('/trips/1');

    await expect(page.getByText('Eiffel Tower')).toBeVisible();
    await expect(page.getByText('Louvre')).toBeVisible();
    await expect(page.getByText('25 €')).toBeVisible();
    await expect(page.getByText('17 €')).toBeVisible();
  });

  test('shows error when adding event with empty name', async ({ page }) => {
    await mockGetTrip(page, { id: 1, name: 'Paris Vacation', events: [] });
    await page.goto('/trips/1');

    await page.getByRole('button', { name: 'Add Event' }).click();
    await expect(page.getByText('Please enter an event name')).toBeVisible();
  });

  test('shows error when adding event without datetime', async ({ page }) => {
    await mockGetTrip(page, { id: 1, name: 'Paris Vacation', events: [] });
    await page.goto('/trips/1');

    await page.getByPlaceholder('Event name (e.g. Dinner)').fill('Dinner');
    await page.getByRole('button', { name: 'Add Event' }).click();
    await expect(page.getByText('Please pick a date and time')).toBeVisible();
  });

  test('adds a new event and renders it', async ({ page }) => {
    await mockGetTrip(page, { id: 1, name: 'Paris Vacation', events: [] });
    await mockAddEvent(page, 1, (body) => ({
      id: 100,
      name: body.name,
      dateTime: body.dateTime,
      kosten: body.kosten,
    }));

    await page.goto('/trips/1');

    await page.getByPlaceholder('Event name (e.g. Dinner)').fill('Dinner');
    await page.locator('input[type="datetime-local"]').fill('2026-06-01T19:00');
    await page.getByPlaceholder('Cost').fill('40');
    await page.getByRole('button', { name: 'Add Event' }).click();

    await expect(page.getByText('Dinner')).toBeVisible();
    await expect(page.getByText('40 €')).toBeVisible();
  });

  test('back link returns to the trip list', async ({ page }) => {
    await mockGetTrip(page, { id: 1, name: 'Paris Vacation', events: [] });
    await page.route('http://localhost:8080/api/trips', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
      } else {
        await route.fallback();
      }
    });
    await page.goto('/trips/1');

    await page.getByRole('link', { name: '← Back to all trips' }).click();
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { name: 'My Trips' })).toBeVisible();
  });
});