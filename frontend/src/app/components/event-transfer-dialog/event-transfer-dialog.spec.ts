import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of, Subject, throwError } from 'rxjs';

import { EventTransferDialog } from './event-transfer-dialog';
import { EventService } from '../../../event-adding-manual/service/event.service';
import { TripService } from '../../TripManagement/services/trip.service';
import { GmailMessagePreview, GmailService } from '../../services/gmail.service';
import { GMAIL_OAUTH_STATE_KEY } from '../gmail-import/gmail-import';
import { ParsedEvent } from '../../../event-adding-manual/models/ParsedEvent';
import { AddEventResponse } from '../../../event-adding-manual/models/AddEventResponse';

describe('EventTransferDialog (Gmail import)', () => {
  let component: EventTransferDialog;
  let fixture: ComponentFixture<EventTransferDialog>;
  let eventServiceSpy: jasmine.SpyObj<EventService>;
  let tripServiceSpy: jasmine.SpyObj<TripService>;
  let gmailServiceSpy: jasmine.SpyObj<GmailService>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<EventTransferDialog>>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  const parsedEvent: ParsedEvent = {
    name: 'Konzert',
    kosten: 49,
    start: '2026-06-01T20:00:00',
    end: '2026-06-01T22:00:00',
  };

  const savedEvent: AddEventResponse = {
    id: '1',
    name: 'Konzert',
    kosten: 49,
    tripId: 1,
    start: '2026-06-01T20:00:00',
    end: '2026-06-01T22:00:00',
  };

  const preview: GmailMessagePreview = {
    id: 'm1',
    subject: 'Concert',
    from: 'sender@example.com',
    date: 'today',
    snippet: 'snippet',
  };

  const preview2: GmailMessagePreview = {
    id: 'm2',
    subject: 'Meeting',
    from: 'other@example.com',
    date: 'today',
    snippet: 'snippet',
  };

  beforeEach(async () => {
    eventServiceSpy = jasmine.createSpyObj<EventService>('EventService', [
      'parseEvent',
      'getConflictEvents',
      'saveParsedEvent',
    ]);
    tripServiceSpy = jasmine.createSpyObj<TripService>('TripService', ['getAllTrips']);
    gmailServiceSpy = jasmine.createSpyObj<GmailService>('GmailService', [
      'getStatus',
      'connect',
      'getMessages',
      'parseMessage',
      'autoParse',
      'buildConsentUrl',
    ]);
    dialogRefSpy = jasmine.createSpyObj<MatDialogRef<EventTransferDialog>>('MatDialogRef', [
      'close',
    ]);
    dialogSpy = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    tripServiceSpy.getAllTrips.and.returnValue(of([]));
    gmailServiceSpy.getStatus.and.returnValue(of({ connected: false, email: null }));
    dialogSpy.open.and.returnValue({ afterClosed: () => of(true) } as never);

    await TestBed.configureTestingModule({
      imports: [EventTransferDialog],
      providers: [
        { provide: EventService, useValue: eventServiceSpy },
        { provide: TripService, useValue: tripServiceSpy },
        { provide: GmailService, useValue: gmailServiceSpy },
        { provide: MatDialog, useValue: dialogSpy },
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: { mode: 'import' } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EventTransferDialog);
    component = fixture.componentInstance;
    // Prevent real top-level navigation during connect tests.
    spyOn(component as unknown as { redirectTo: (url: string) => void }, 'redirectTo');
    sessionStorage.clear();
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('checks Gmail connection status once when the source switches to gmail', () => {
    gmailServiceSpy.getStatus.and.returnValue(of({ connected: true, email: 'user@gmail.com' }));

    component.importSource = 'gmail';
    component.onImportSourceChange();
    component.onImportSourceChange();

    expect(gmailServiceSpy.getStatus).toHaveBeenCalledTimes(1);
    expect(component.gmailConnected).toBeTrue();
    expect(component.gmailEmail).toBe('user@gmail.com');
  });

  it('shows the Connect button when Gmail is not connected', () => {
    component.importSource = 'gmail';
    component.onImportSourceChange();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const connectButton = Array.from(host.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Connect Gmail'),
    );
    expect(connectButton).toBeTruthy();
    expect(component.gmailConnected).toBeFalse();
  });

  it('starts the OAuth flow: stores a state and redirects to the consent URL', () => {
    gmailServiceSpy.buildConsentUrl.and.returnValue(
      'https://accounts.google.com/o/oauth2/v2/auth?x=1',
    );

    component.connectGmail();

    const storedState = sessionStorage.getItem(GMAIL_OAUTH_STATE_KEY);
    expect(storedState).toBeTruthy();
    expect(gmailServiceSpy.buildConsentUrl).toHaveBeenCalledWith(storedState!);
    const redirect = (component as unknown as { redirectTo: jasmine.Spy }).redirectTo;
    expect(redirect).toHaveBeenCalledWith('https://accounts.google.com/o/oauth2/v2/auth?x=1');
  });

  it('loads recent messages and selects them all by default', () => {
    gmailServiceSpy.getMessages.and.returnValue(of([preview, preview2]));

    component.loadGmailMessages();

    expect(component.gmailMessages).toEqual([preview, preview2]);
    expect(component.selectedGmailCount).toBe(2);
    expect(component.allGmailSelected).toBeTrue();
    expect(component.isLoadingGmailMessages).toBeFalse();
  });

  it('toggles a single message and the select-all control', () => {
    gmailServiceSpy.getMessages.and.returnValue(of([preview, preview2]));
    component.loadGmailMessages();

    component.toggleGmailSelection('m1');
    expect(component.isGmailSelected('m1')).toBeFalse();
    expect(component.selectedGmailCount).toBe(1);
    expect(component.allGmailSelected).toBeFalse();

    component.toggleSelectAllGmail();
    expect(component.selectedGmailCount).toBe(2);

    component.toggleSelectAllGmail();
    expect(component.selectedGmailCount).toBe(0);
  });

  it('auto mode: imports only the event-containing messages and closes with the saved events', () => {
    // Backend already filtered out non-event mails; it returns one event for two selected messages.
    gmailServiceSpy.autoParse.and.returnValue(of([parsedEvent]));
    eventServiceSpy.saveParsedEvent.and.returnValue(of(savedEvent));

    component.tripId = 1;
    component.gmailImportMode = 'auto';
    component.gmailMessages = [preview, preview2];
    component.selectedGmailIds = new Set(['m1', 'm2']);

    component.importSelectedGmailMessages();

    expect(gmailServiceSpy.autoParse).toHaveBeenCalledWith(['m1', 'm2']);
    expect(gmailServiceSpy.parseMessage).not.toHaveBeenCalled();
    expect(eventServiceSpy.saveParsedEvent).toHaveBeenCalledTimes(1);
    expect(eventServiceSpy.saveParsedEvent).toHaveBeenCalledWith(parsedEvent, 1);
    expect(dialogSpy.open).not.toHaveBeenCalled();
    expect(dialogRefSpy.close).toHaveBeenCalledWith([savedEvent]);
    expect(component.isImportingGmail).toBeFalse();
  });

  it('auto mode: shows a message and saves nothing when no message contains an event', () => {
    gmailServiceSpy.autoParse.and.returnValue(of([]));

    component.gmailImportMode = 'auto';
    component.gmailMessages = [preview];
    component.selectedGmailIds = new Set(['m1']);

    component.importSelectedGmailMessages();

    expect(eventServiceSpy.saveParsedEvent).not.toHaveBeenCalled();
    expect(dialogRefSpy.close).not.toHaveBeenCalled();
    expect(dialogSpy.open).toHaveBeenCalled();
    expect(component.isImportingGmail).toBeFalse();
  });

  it('review mode: opens the review dialog and saves the confirmed events', () => {
    gmailServiceSpy.parseMessage.and.returnValue(of(parsedEvent));
    eventServiceSpy.saveParsedEvent.and.returnValue(of(savedEvent));
    dialogSpy.open.and.returnValue({ afterClosed: () => of([parsedEvent]) } as never);

    component.gmailImportMode = 'review';
    component.gmailMessages = [preview];
    component.selectedGmailIds = new Set(['m1']);

    component.importSelectedGmailMessages();

    expect(dialogSpy.open).toHaveBeenCalled();
    expect(eventServiceSpy.saveParsedEvent).toHaveBeenCalledWith(parsedEvent, undefined);
    expect(dialogRefSpy.close).toHaveBeenCalledWith([savedEvent]);
  });

  it('review mode: saves nothing when the review dialog is cancelled', () => {
    gmailServiceSpy.parseMessage.and.returnValue(of(parsedEvent));
    eventServiceSpy.saveParsedEvent.and.returnValue(of(savedEvent));
    dialogSpy.open.and.returnValue({ afterClosed: () => of(undefined) } as never);

    component.gmailImportMode = 'review';
    component.gmailMessages = [preview];
    component.selectedGmailIds = new Set(['m1']);

    component.importSelectedGmailMessages();

    expect(eventServiceSpy.saveParsedEvent).not.toHaveBeenCalled();
    expect(dialogRefSpy.close).not.toHaveBeenCalled();
  });

  it('keeps the loading state active while auto import is pending', () => {
    const pending = new Subject<ParsedEvent[]>();
    gmailServiceSpy.autoParse.and.returnValue(pending.asObservable());
    eventServiceSpy.saveParsedEvent.and.returnValue(of(savedEvent));

    component.gmailImportMode = 'auto';
    component.gmailMessages = [preview];
    component.selectedGmailIds = new Set(['m1']);

    component.importSelectedGmailMessages();
    expect(component.isImportingGmail).toBeTrue();

    pending.next([parsedEvent]);
    pending.complete();
    expect(component.isImportingGmail).toBeFalse();
  });

  it('review mode: shows a message and saves nothing when every message fails to parse', () => {
    gmailServiceSpy.parseMessage.and.returnValue(throwError(() => ({ error: 'kaputt' })));

    component.gmailImportMode = 'review';
    component.gmailMessages = [preview];
    component.selectedGmailIds = new Set(['m1']);

    component.importSelectedGmailMessages();

    expect(component.isImportingGmail).toBeFalse();
    expect(eventServiceSpy.saveParsedEvent).not.toHaveBeenCalled();
    expect(dialogSpy.open).toHaveBeenCalled();
  });

  it('does not call the backend when no message is selected', () => {
    component.selectedGmailIds = new Set();

    component.importSelectedGmailMessages();

    expect(gmailServiceSpy.parseMessage).not.toHaveBeenCalled();
    expect(dialogSpy.open).toHaveBeenCalled();
  });
});
