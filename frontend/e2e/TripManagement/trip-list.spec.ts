import { test, expect } from '@playwright/test';
import { mockGetAllTrips, mockCreateTrip } from './helpers';

test.describe('Trip List page', () => {
  test('shows the page heading', async ({ page }) => {
    await mockGetAllTrips(page, []);
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'My Trips' })).toBeVisible();
  });

  test('shows empty-state message when no trips exist', async ({ page }) => {
    await mockGetAllTrips(page, []);
    await page.goto('/');
    await expect(page.getByText('No trips yet. Create your first one above!')).toBeVisible();
  });

  test('renders a trip in the list', async ({ page }) => {
    await mockGetAllTrips(page, [
      { id: 1, name: 'Paris Vacation', events: [] },
      { id: 2, name: 'Berlin Trip', events: [{ id: 5, name: 'x', dateTime: '2026-01-01T10:00:00', kosten: 0 }] },
    ]);
    await page.goto('/');

    await expect(page.getByText('Paris Vacation')).toBeVisible();
    await expect(page.getByText('Berlin Trip')).toBeVisible();
    await expect(page.getByText('0 events').first()).toBeVisible();
    await expect(page.getByText('1 events').first()).toBeVisible();
  });

  test('shows error when creating with empty name', async ({ page }) => {
    await mockGetAllTrips(page, []);
    await page.goto('/');

    await page.getByRole('button', { name: 'Create Trip' }).click();
    await expect(page.getByText('Please enter a trip name')).toBeVisible();
  });

  test('creates a new trip and adds it to the list', async ({ page }) => {
    await mockGetAllTrips(page, []);
    await mockCreateTrip(page, (name) => ({ id: 99, name, events: [] }));

    await page.goto('/');
    await page.getByPlaceholder('Trip name (e.g. Paris Vacation)').fill('Tokyo Trip');
    await page.getByRole('button', { name: 'Create Trip' }).click();

    await expect(page.getByText('Tokyo Trip')).toBeVisible();
  });
});