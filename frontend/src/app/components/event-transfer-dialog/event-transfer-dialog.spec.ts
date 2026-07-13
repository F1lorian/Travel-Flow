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
      'autoParse',
      'disconnect',
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

  it('offers to switch account when Gmail is connected', () => {
    gmailServiceSpy.getStatus.and.returnValue(of({ connected: true, email: 'user@gmail.com' }));

    component.importSource = 'gmail';
    component.onImportSourceChange();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const switchButton = Array.from(host.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Use another account'),
    );
    expect(switchButton).toBeTruthy();
  });

  it('disconnecting clears the connection and the loaded messages', () => {
    gmailServiceSpy.disconnect.and.returnValue(of(void 0));
    component.gmailConnected = true;
    component.gmailEmail = 'user@gmail.com';
    component.gmailMessages = [preview, preview2];
    component.selectedGmailIds = new Set(['m1', 'm2']);

    component.disconnectGmail();

    expect(gmailServiceSpy.disconnect).toHaveBeenCalled();
    expect(component.gmailConnected).toBeFalse();
    expect(component.gmailEmail).toBeNull();
    expect(component.gmailMessages).toEqual([]);
    expect(component.selectedGmailCount).toBe(0);
    expect(component.isDisconnectingGmail).toBeFalse();
  });

  it('shows the Connect button again after disconnecting', () => {
    gmailServiceSpy.getStatus.and.returnValue(of({ connected: true, email: 'user@gmail.com' }));
    gmailServiceSpy.disconnect.and.returnValue(of(void 0));

    component.importSource = 'gmail';
    component.onImportSourceChange();
    component.disconnectGmail();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const connectButton = Array.from(host.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Connect Gmail'),
    );
    expect(connectButton).toBeTruthy();
  });

  it('keeps the connection when disconnecting fails', () => {
    gmailServiceSpy.disconnect.and.returnValue(throwError(() => ({ error: 'boom' })));
    component.gmailConnected = true;
    component.gmailEmail = 'user@gmail.com';

    component.disconnectGmail();

    expect(component.gmailConnected).toBeTrue();
    expect(component.gmailEmail).toBe('user@gmail.com');
    expect(component.isDisconnectingGmail).toBeFalse();
    expect(dialogSpy.open).toHaveBeenCalled();
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

  it('imports only the event-containing messages and closes with the saved events', () => {
    // Backend already filtered out non-event and unnamed mails; it returns one event for two selected.
    gmailServiceSpy.autoParse.and.returnValue(of({ events: [parsedEvent], failed: 0 }));
    eventServiceSpy.saveParsedEvent.and.returnValue(of(savedEvent));

    component.tripId = 1;
    component.gmailMessages = [preview, preview2];
    component.selectedGmailIds = new Set(['m1', 'm2']);

    component.importSelectedGmailMessages();

    expect(gmailServiceSpy.autoParse).toHaveBeenCalledWith(['m1', 'm2']);
    expect(eventServiceSpy.saveParsedEvent).toHaveBeenCalledTimes(1);
    expect(eventServiceSpy.saveParsedEvent).toHaveBeenCalledWith(parsedEvent, 1);
    expect(dialogSpy.open).not.toHaveBeenCalled();
    expect(dialogRefSpy.close).toHaveBeenCalledWith([savedEvent]);
    expect(component.isImportingGmail).toBeFalse();
  });

  it('shows a message and saves nothing when no message contains an event', () => {
    gmailServiceSpy.autoParse.and.returnValue(of({ events: [], failed: 0 }));

    component.gmailMessages = [preview];
    component.selectedGmailIds = new Set(['m1']);

    component.importSelectedGmailMessages();

    expect(eventServiceSpy.saveParsedEvent).not.toHaveBeenCalled();
    expect(dialogRefSpy.close).not.toHaveBeenCalled();
    expect(dialogSpy.open).toHaveBeenCalled();
    expect(component.isImportingGmail).toBeFalse();
  });

  it('reports when messages could not be checked instead of saving', () => {
    gmailServiceSpy.autoParse.and.returnValue(of({ events: [], failed: 2 }));

    component.gmailMessages = [preview, preview2];
    component.selectedGmailIds = new Set(['m1', 'm2']);

    component.importSelectedGmailMessages();

    expect(eventServiceSpy.saveParsedEvent).not.toHaveBeenCalled();
    expect(dialogRefSpy.close).not.toHaveBeenCalled();
    // A message dialog is shown telling the user to retry.
    expect(dialogSpy.open).toHaveBeenCalled();
    expect(component.isImportingGmail).toBeFalse();
  });

  it('keeps the loading state active while import is pending', () => {
    const pending = new Subject<{ events: ParsedEvent[]; failed: number }>();
    gmailServiceSpy.autoParse.and.returnValue(pending.asObservable());
    eventServiceSpy.saveParsedEvent.and.returnValue(of(savedEvent));

    component.gmailMessages = [preview];
    component.selectedGmailIds = new Set(['m1']);

    component.importSelectedGmailMessages();
    expect(component.isImportingGmail).toBeTrue();

    pending.next({ events: [parsedEvent], failed: 0 });
    pending.complete();
    expect(component.isImportingGmail).toBeFalse();
  });

  it('does not call the backend when no message is selected', () => {
    component.selectedGmailIds = new Set();

    component.importSelectedGmailMessages();

    expect(gmailServiceSpy.autoParse).not.toHaveBeenCalled();
    expect(dialogSpy.open).toHaveBeenCalled();
  });
});
