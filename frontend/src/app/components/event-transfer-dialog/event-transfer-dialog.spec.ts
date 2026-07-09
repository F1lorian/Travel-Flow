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

  it('loads recent messages and preselects the first when connected', () => {
    gmailServiceSpy.getMessages.and.returnValue(of([preview]));

    component.loadGmailMessages();

    expect(component.gmailMessages).toEqual([preview]);
    expect(component.selectedGmailMessageId).toBe('m1');
    expect(component.isLoadingGmailMessages).toBeFalse();
  });

  it('imports the selected Gmail message and saves it when there is no conflict', () => {
    gmailServiceSpy.parseMessage.and.returnValue(of(parsedEvent));
    eventServiceSpy.getConflictEvents.and.returnValue(of([]));
    eventServiceSpy.saveParsedEvent.and.returnValue(of(savedEvent));

    component.tripId = 1;
    component.selectedGmailMessageId = 'm1';
    component.importSelectedGmailMessage();

    expect(gmailServiceSpy.parseMessage).toHaveBeenCalledWith('m1');
    expect(eventServiceSpy.getConflictEvents).toHaveBeenCalledWith(
      parsedEvent.start,
      parsedEvent.end,
    );
    expect(dialogSpy.open).not.toHaveBeenCalled();
    expect(eventServiceSpy.saveParsedEvent).toHaveBeenCalledWith(parsedEvent, 1);
    expect(dialogRefSpy.close).toHaveBeenCalledWith(savedEvent);
    expect(component.isImportingGmail).toBeFalse();
  });

  it('routes the parsed Gmail event into the conflict dialog and saves when confirmed', () => {
    gmailServiceSpy.parseMessage.and.returnValue(of(parsedEvent));
    eventServiceSpy.getConflictEvents.and.returnValue(of([savedEvent]));
    eventServiceSpy.saveParsedEvent.and.returnValue(of(savedEvent));
    dialogSpy.open.and.returnValue({ afterClosed: () => of(true) } as never);

    component.selectedGmailMessageId = 'm1';
    component.importSelectedGmailMessage();

    expect(dialogSpy.open).toHaveBeenCalled();
    expect(eventServiceSpy.saveParsedEvent).toHaveBeenCalledWith(parsedEvent, undefined);
    expect(dialogRefSpy.close).toHaveBeenCalledWith(savedEvent);
  });

  it('does not save when the user cancels the conflict dialog', () => {
    gmailServiceSpy.parseMessage.and.returnValue(of(parsedEvent));
    eventServiceSpy.getConflictEvents.and.returnValue(of([savedEvent]));
    eventServiceSpy.saveParsedEvent.and.returnValue(of(savedEvent));
    dialogSpy.open.and.returnValue({ afterClosed: () => of(false) } as never);

    component.selectedGmailMessageId = 'm1';
    component.importSelectedGmailMessage();

    expect(eventServiceSpy.saveParsedEvent).not.toHaveBeenCalled();
    expect(dialogRefSpy.close).not.toHaveBeenCalled();
  });

  it('keeps the loading state active while the parse request is pending', () => {
    const pending = new Subject<ParsedEvent>();
    gmailServiceSpy.parseMessage.and.returnValue(pending.asObservable());
    eventServiceSpy.getConflictEvents.and.returnValue(of([]));
    eventServiceSpy.saveParsedEvent.and.returnValue(of(savedEvent));

    component.selectedGmailMessageId = 'm1';
    component.importSelectedGmailMessage();
    expect(component.isImportingGmail).toBeTrue();

    pending.next(parsedEvent);
    pending.complete();
    expect(component.isImportingGmail).toBeFalse();
  });

  it('clears the loading state and shows a message when parsing fails', () => {
    gmailServiceSpy.parseMessage.and.returnValue(throwError(() => ({ error: 'kaputt' })));

    component.selectedGmailMessageId = 'm1';
    component.importSelectedGmailMessage();

    expect(component.isImportingGmail).toBeFalse();
    expect(eventServiceSpy.saveParsedEvent).not.toHaveBeenCalled();
    expect(dialogSpy.open).toHaveBeenCalled();
  });

  it('does not call the backend when no message is selected', () => {
    component.selectedGmailMessageId = undefined;

    component.importSelectedGmailMessage();

    expect(gmailServiceSpy.parseMessage).not.toHaveBeenCalled();
    expect(dialogSpy.open).toHaveBeenCalled();
  });
});
