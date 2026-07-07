import { test, expect } from '@playwright/test';
import { authenticate, mockFriends, mockJson } from './helpers';

const permissions = [
  {
    friendId: 2,
    username: 'sveto',
    firstName: 'Svetoslav',
    surname: 'Ivanov',
    canSeeEvents: false,
    canSeeProfile: true,
  },
];

const friends = [
  {
    id: 2,
    username: 'sveto',
    firstName: 'Svetoslav',
    surname: 'Ivanov',
  },
];

test.beforeEach(async ({ page }) => {
  await authenticate(page);
});

test('Privacy Settings page is shown with friend permissions', async ({ page }) => {
  await mockJson(page, '**/api/privacy/permissions', permissions);

  await page.goto('/privacy');

  await expect(page.getByRole('heading', { name: 'Privacy Settings' })).toBeVisible();
  await expect(
    page.getByText('Decide which friends can see your profile information and events.'),
  ).toBeVisible();

  await expect(page.getByText('Svetoslav Ivanov')).toBeVisible();
  await expect(page.getByText('sveto', { exact: true })).toBeVisible();

  await expect(page.getByText('Can see events')).toBeVisible();
  await expect(page.getByText('Can see profile')).toBeVisible();
});

test('Privacy Settings page shows empty state when no friends exist', async ({ page }) => {
  await mockJson(page, '**/api/privacy/permissions', []);

  await page.goto('/privacy');

  await expect(page.getByRole('heading', { name: 'No friends found' })).toBeVisible();
  await expect(page.getByText('Add friends first before configuring privacy settings.')).toBeVisible();
});

test('User can update event permission for a friend', async ({ page }) => {
  await mockJson(page, '**/api/privacy/permissions', permissions);

  let requestBody: unknown = null;

  await page.route('**/api/privacy/permissions/2', async (route) => {
    if (route.request().method() !== 'PUT') {
      await route.fallback();
      return;
    }

    requestBody = route.request().postDataJSON();

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        ...permissions[0],
        canSeeEvents: true,
        canSeeProfile: true,
      }),
    });
  });

  await page.goto('/privacy');

  const eventsCheckbox = page.getByRole('checkbox', { name: 'Events' });
  await expect(eventsCheckbox).not.toBeChecked();

  await eventsCheckbox.check();

  expect(requestBody).toEqual({
    canSeeEvents: true,
    canSeeProfile: true,
  });

  await expect(page.getByText('Privacy settings have been updated.')).toBeVisible();
  await expect(eventsCheckbox).toBeChecked();
});

test('User can update profile permission for a friend', async ({ page }) => {
  await mockJson(page, '**/api/privacy/permissions', permissions);

  let requestBody: unknown = null;

  await page.route('**/api/privacy/permissions/2', async (route) => {
    if (route.request().method() !== 'PUT') {
      await route.fallback();
      return;
    }

    requestBody = route.request().postDataJSON();

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        ...permissions[0],
        canSeeEvents: false,
        canSeeProfile: false,
      }),
    });
  });

  await page.goto('/privacy');

  const profileCheckbox = page.getByRole('checkbox', { name: 'Profile' });
  await expect(profileCheckbox).toBeChecked();

  await profileCheckbox.uncheck();

  expect(requestBody).toEqual({
    canSeeEvents: false,
    canSeeProfile: false,
  });

  await expect(page.getByText('Privacy settings have been updated.')).toBeVisible();
  await expect(profileCheckbox).not.toBeChecked();
});

test('Privacy settings can be opened from Friends page', async ({ page }) => {
  await mockFriends(page, friends);
  await mockJson(page, '**/api/privacy/permissions', permissions);

  await page.goto('/friends');

  await expect(page.getByRole('heading', { name: 'Friends', exact: true })).toBeVisible();

  await page.getByRole('link', { name: 'Manage privacy settings' }).click();

  await expect(page).toHaveURL(/\/privacy$/);
  await expect(page.getByRole('heading', { name: 'Privacy Settings' })).toBeVisible();
});
