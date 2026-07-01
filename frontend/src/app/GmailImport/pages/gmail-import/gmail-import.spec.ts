import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { GmailImportComponent } from './gmail-import';
import { GmailImportService } from '../../services/gmail-import.service';
import { ExtractedEvent } from '../../models/gmail-import.models';

describe('GmailImportComponent', () => {
  let fixture: ComponentFixture<GmailImportComponent>;
  let component: GmailImportComponent;
  let serviceSpy: jasmine.SpyObj<GmailImportService>;

  function configure(queryParams: Record<string, string> = {}) {
    serviceSpy = jasmine.createSpyObj<GmailImportService>('GmailImportService', [
      'getStatus',
      'getAuthUrl',
      'connect',
      'disconnect',
      'scan',
      'importEvent'
    ]);
    serviceSpy.getStatus.and.returnValue(of({ connected: true, emailAddress: 'a@gmail.com' }));
    serviceSpy.connect.and.returnValue(of({ connected: true, emailAddress: 'a@gmail.com' }));

    TestBed.configureTestingModule({
      imports: [GmailImportComponent],
      providers: [
        { provide: GmailImportService, useValue: serviceSpy },
        { provide: Router, useValue: { navigate: jasmine.createSpy('navigate') } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } }
        }
      ]
    });

    fixture = TestBed.createComponent(GmailImportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('loads the connection status when there is no OAuth code', () => {
    configure();
    expect(serviceSpy.getStatus).toHaveBeenCalled();
    expect(component.connected()).toBeTrue();
    expect(component.emailAddress()).toBe('a@gmail.com');
  });

  it('completes the connection when redirected back with a code', () => {
    configure({ code: 'auth-code' });
    expect(serviceSpy.connect).toHaveBeenCalledWith('auth-code');
    expect(serviceSpy.getStatus).not.toHaveBeenCalled();
    expect(component.successMessage()).toContain('connected');
  });

  it('lists extracted events after scanning', () => {
    configure();
    const events: ExtractedEvent[] = [
      {
        sourceMessageId: '1',
        title: 'Concert',
        description: null,
        date: '2026-06-01',
        startTime: '20:00:00',
        endTime: null,
        timeZone: null,
        location: 'Arena',
        organizer: null
      }
    ];
    serviceSpy.scan.and.returnValue(of(events));

    component.scanEmails();

    expect(component.candidates().length).toBe(1);
    expect(component.hasScanned()).toBeTrue();
  });

  it('pre-fills the form and normalizes the time when reviewing', () => {
    configure();
    component.reviewEvent({
      sourceMessageId: '1',
      title: 'Concert',
      description: null,
      date: '2026-06-01',
      startTime: '20:00:00',
      endTime: '22:00:00',
      timeZone: null,
      location: 'Arena',
      organizer: null
    });

    expect(component.selected()).not.toBeNull();
    expect(component.form.title).toBe('Concert');
    expect(component.form.startTime).toBe('20:00');
    expect(component.form.endTime).toBe('22:00');
  });

  it('refuses to create an event without a date', () => {
    configure();
    component.form.date = '';

    component.createEvent();

    expect(serviceSpy.importEvent).not.toHaveBeenCalled();
    expect(component.errorMessage()).toContain('date');
  });

  it('creates an event and shows a success message', () => {
    configure();
    serviceSpy.importEvent.and.returnValue(
      of({ id: '9', title: 'Dinner', start: '', end: '', message: 'Event created successfully' })
    );
    component.reviewEvent({
      sourceMessageId: '1',
      title: 'Dinner',
      description: null,
      date: '2026-06-01',
      startTime: '19:00:00',
      endTime: '21:00:00',
      timeZone: null,
      location: 'Bistro',
      organizer: null
    });

    component.createEvent();

    expect(serviceSpy.importEvent).toHaveBeenCalled();
    expect(component.successMessage()).toContain('Event created successfully');
    expect(component.selected()).toBeNull();
  });
});
