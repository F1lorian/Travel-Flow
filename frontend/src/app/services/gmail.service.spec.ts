import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import {
  GmailAutoImportResult,
  GmailConnection,
  GmailMessagePreview,
  GmailService,
} from './gmail.service';

describe('GmailService', () => {
  let service: GmailService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [GmailService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(GmailService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getStatus() GETs /api/gmail/status', () => {
    const expected: GmailConnection = { connected: true, email: 'user@gmail.com' };
    let actual: GmailConnection | undefined;

    service.getStatus().subscribe((res) => (actual = res));

    const req = httpMock.expectOne('/api/gmail/status');
    expect(req.request.method).toBe('GET');
    req.flush(expected);

    expect(actual).toEqual(expected);
  });

  it('connect() POSTs the auth code to /api/gmail/connect', () => {
    const expected: GmailConnection = { connected: true, email: 'user@gmail.com' };
    let actual: GmailConnection | undefined;

    service.connect('auth-code').subscribe((res) => (actual = res));

    const req = httpMock.expectOne('/api/gmail/connect');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: 'auth-code' });
    req.flush(expected);

    expect(actual).toEqual(expected);
  });

  it('getMessages() GETs /api/gmail/messages', () => {
    const previews: GmailMessagePreview[] = [
      { id: 'm1', subject: 'Concert', from: 'sender@x.com', date: 'today', snippet: 'snippet' },
    ];
    let actual: GmailMessagePreview[] | undefined;

    service.getMessages().subscribe((res) => (actual = res));

    const req = httpMock.expectOne('/api/gmail/messages');
    expect(req.request.method).toBe('GET');
    req.flush(previews);

    expect(actual).toEqual(previews);
  });

  it('autoParse() POSTs the message ids to /api/gmail/import/auto', () => {
    const expected: GmailAutoImportResult = {
      events: [{ name: 'Konzert', kosten: 49, start: '2026-06-01T20:00:00', end: '2026-06-01T22:00:00' }],
      failed: 1,
    };
    let actual: GmailAutoImportResult | undefined;

    service.autoParse(['m1', 'm2']).subscribe((res) => (actual = res));

    const req = httpMock.expectOne('/api/gmail/import/auto');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ messageIds: ['m1', 'm2'] });
    req.flush(expected);

    expect(actual).toEqual(expected);
  });

  it('propagates HTTP errors to the caller', () => {
    let errored = false;

    service.getStatus().subscribe({
      next: () => fail('expected an error'),
      error: () => (errored = true),
    });

    httpMock
      .expectOne('/api/gmail/status')
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(errored).toBeTrue();
  });

  describe('buildConsentUrl()', () => {
    it('builds a Google consent URL for read-only Gmail access', () => {
      const url = new URL(service.buildConsentUrl('state-123'));

      expect(url.origin + url.pathname).toBe('https://accounts.google.com/o/oauth2/v2/auth');

      const params = url.searchParams;
      expect(params.get('response_type')).toBe('code');
      expect(params.get('scope')).toBe('https://www.googleapis.com/auth/gmail.readonly');
      expect(params.get('access_type')).toBe('offline');
      expect(params.get('prompt')).toBe('consent');
      expect(params.get('include_granted_scopes')).toBe('true');
      expect(params.get('state')).toBe('state-123');
      expect(params.get('redirect_uri')).toBe(`${window.location.origin}/gmail-import`);
      expect(params.has('client_id')).toBeTrue();
    });

    it('echoes the given state so the redirect can verify it', () => {
      const url = new URL(service.buildConsentUrl('another&state=value'));
      expect(url.searchParams.get('state')).toBe('another&state=value');
    });
  });
});
