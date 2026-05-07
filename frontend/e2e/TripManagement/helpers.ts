import { Page } from '@playwright/test';

export const USE_REAL_BACKEND = process.env.USE_REAL_BACKEND === '1';

export const API_BASE = 'http://localhost:8080/api/trips';

export interface MockTrip {
  id: number;
  name: string;
  events: MockEvent[];
}

export interface MockEvent {
  id: number;
  name: string;
  dateTime: string;
  kosten: number;
}

export async function mockGetAllTrips(page: Page, trips: MockTrip[]) {
  if (USE_REAL_BACKEND) return;
  await page.route(API_BASE, async (route) => {
    if (route.request().method() !== 'GET') return route.fallback();
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(trips),
    });
  });
}

export async function mockCreateTrip(page: Page, makeResponse: (name: string) => MockTrip) {
  if (USE_REAL_BACKEND) return;
  await page.route(API_BASE, async (route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    const body = route.request().postDataJSON() as { name: string };
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(makeResponse(body.name)),
    });
  });
}

export async function mockGetTrip(page: Page, trip: MockTrip) {
  if (USE_REAL_BACKEND) return;
  await page.route(`${API_BASE}/${trip.id}`, async (route) => {
    if (route.request().method() !== 'GET') return route.fallback();
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(trip),
    });
  });
}

export async function mockAddEvent(
  page: Page,
  tripId: number,
  makeResponse: (body: { name: string; dateTime: string; kosten: number }) => MockEvent,
) {
  if (USE_REAL_BACKEND) return;
  await page.route(`${API_BASE}/${tripId}/events`, async (route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    const body = route.request().postDataJSON();
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(makeResponse(body)),
    });
  });
}