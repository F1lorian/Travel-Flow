import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { GmailImportService } from './gmail-import.service';
import { ExtractedEvent } from '../models/gmail-import.models';

describe('GmailImportService', () => {
  let service: GmailImportService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [GmailImportService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(GmailImportService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('requests the connection status', () => {
    service.getStatus().subscribe();
    const req = httpMock.expectOne('/api/gmail/status');
    expect(req.request.method).toBe('GET');
    req.flush({ connected: true, emailAddress: 'a@gmail.com' });
  });

  it('scans recent emails', () => {
    let result: ExtractedEvent[] | undefined;
    service.scan().subscribe((events) => (result = events));

    const req = httpMock.expectOne('/api/gmail/scan');
    expect(req.request.method).toBe('GET');
    req.flush([{ sourceMessageId: '1', title: 'Concert' } as ExtractedEvent]);

    expect(result?.length).toBe(1);
    expect(result?.[0].title).toBe('Concert');
  });

  it('posts the confirmed event to the import endpoint', () => {
    const request = {
      title: 'Dinner',
      description: null,
      date: '2026-06-01',
      startTime: '19:00',
      endTime: '21:00',
      kosten: 25,
      tripId: null
    };
    service.importEvent(request).subscribe();

    const req = httpMock.expectOne('/api/gmail/import');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ id: '5', title: 'Dinner', start: '', end: '', message: 'Event created successfully' });
  });

  it('sends the authorization code when connecting', () => {
    service.connect('auth-code').subscribe();
    const req = httpMock.expectOne('/api/gmail/connect');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: 'auth-code' });
    req.flush({ connected: true, emailAddress: 'a@gmail.com' });
  });
});
