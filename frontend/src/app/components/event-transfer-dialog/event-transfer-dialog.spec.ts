import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of, Subject, throwError } from 'rxjs';

import { EventTransferDialog } from './event-transfer-dialog';
import { EventService } from '../../../event-adding-manual/service/event.service';
import { TripService } from '../../TripManagement/services/trip.service';
import { ParsedEvent } from '../../../event-adding-manual/models/ParsedEvent';
import { AddEventResponse } from '../../../event-adding-manual/models/AddEventResponse';

describe('EventTransferDialog (Gmail import)', () => {
  let component: EventTransferDialog;
  let fixture: ComponentFixture<EventTransferDialog>;
  let eventServiceSpy: jasmine.SpyObj<EventService>;
  let tripServiceSpy: jasmine.SpyObj<TripService>;
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

  function emlFile(name = 'event.eml'): File {
    return new File(['dummy'], name, { type: 'message/rfc822' });
  }

  function fileInputEvent(file?: File): Event {
    return { target: { files: file ? [file] : [] } } as unknown as Event;
  }

  beforeEach(async () => {
    eventServiceSpy = jasmine.createSpyObj<EventService>('EventService', [
      'parseEvent',
      'getConflictEvents',
      'saveParsedEvent',
    ]);
    tripServiceSpy = jasmine.createSpyObj<TripService>('TripService', ['getAllTrips']);
    dialogRefSpy = jasmine.createSpyObj<MatDialogRef<EventTransferDialog>>('MatDialogRef', ['close']);
    dialogSpy = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    // ngOnInit loads trips; keep it empty so the dialog renders cleanly.
    tripServiceSpy.getAllTrips.and.returnValue(of([]));
    // Any dialog opened (conflict confirmation / message) resolves to "confirmed" by default.
    dialogSpy.open.and.returnValue({ afterClosed: () => of(true) } as never);

    await TestBed.configureTestingModule({
      imports: [EventTransferDialog],
      providers: [
        { provide: EventService, useValue: eventServiceSpy },
        { provide: TripService, useValue: tripServiceSpy },
        { provide: MatDialog, useValue: dialogSpy },
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: { mode: 'import' } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EventTransferDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders the Gmail .eml controls when gmail is selected as the import source', () => {
    component.importSource = 'gmail';
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const fileInput = host.querySelector('#gmail-file') as HTMLInputElement | null;
    const importButton = Array.from(host.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Import from Gmail'),
    );

    expect(fileInput).toBeTruthy();
    expect(fileInput?.accept).toBe('.eml');
    expect(importButton).toBeTruthy();
  });

  it('accepts a selected .eml file', () => {
    const file = emlFile('invite.eml');

    component.onFileSelected(fileInputEvent(file));

    expect(component.file).toBe(file);
    expect(component.fileName).toBe('invite.eml');
  });

  it('rejects a non-.eml file and shows a message', () => {
    component.onFileSelected(fileInputEvent(new File(['x'], 'invite.pdf')));

    expect(component.file).toBeUndefined();
    expect(component.fileName).toBe('');
    expect(dialogSpy.open).toHaveBeenCalled();
  });

  it('parses the Gmail .eml and saves it when there is no conflict', () => {
    eventServiceSpy.parseEvent.and.returnValue(of(parsedEvent));
    eventServiceSpy.getConflictEvents.and.returnValue(of([]));
    eventServiceSpy.saveParsedEvent.and.returnValue(of(savedEvent));

    component.importSource = 'gmail';
    component.tripId = 1;
    component.file = emlFile();

    component.uploadGmailFile();

    const [formData, tripId] = eventServiceSpy.parseEvent.calls.mostRecent().args;
    expect(formData instanceof FormData).toBeTrue();
    expect((formData as FormData).get('MailFile')).toBe(component.file);
    expect(tripId).toBe(1);
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
    eventServiceSpy.parseEvent.and.returnValue(of(parsedEvent));
    eventServiceSpy.getConflictEvents.and.returnValue(of([savedEvent]));
    eventServiceSpy.saveParsedEvent.and.returnValue(of(savedEvent));
    dialogSpy.open.and.returnValue({ afterClosed: () => of(true) } as never);

    component.file = emlFile();
    component.uploadGmailFile();

    expect(dialogSpy.open).toHaveBeenCalled();
    expect(eventServiceSpy.saveParsedEvent).toHaveBeenCalledWith(parsedEvent, undefined);
    expect(dialogRefSpy.close).toHaveBeenCalledWith(savedEvent);
  });

  it('does not save when the user cancels the conflict dialog', () => {
    eventServiceSpy.parseEvent.and.returnValue(of(parsedEvent));
    eventServiceSpy.getConflictEvents.and.returnValue(of([savedEvent]));
    eventServiceSpy.saveParsedEvent.and.returnValue(of(savedEvent));
    dialogSpy.open.and.returnValue({ afterClosed: () => of(false) } as never);

    component.file = emlFile();
    component.uploadGmailFile();

    expect(eventServiceSpy.saveParsedEvent).not.toHaveBeenCalled();
    expect(dialogRefSpy.close).not.toHaveBeenCalled();
  });

  it('keeps the loading state active while the parse request is pending', () => {
    const pending = new Subject<ParsedEvent>();
    eventServiceSpy.parseEvent.and.returnValue(pending.asObservable());
    eventServiceSpy.getConflictEvents.and.returnValue(of([]));
    eventServiceSpy.saveParsedEvent.and.returnValue(of(savedEvent));

    component.file = emlFile();
    component.uploadGmailFile();
    expect(component.isImportingGmail).toBeTrue();

    pending.next(parsedEvent);
    pending.complete();
    expect(component.isImportingGmail).toBeFalse();
  });

  it('clears the loading state and shows a message when parsing fails', () => {
    eventServiceSpy.parseEvent.and.returnValue(throwError(() => ({ error: 'kaputt' })));

    component.file = emlFile();
    component.uploadGmailFile();

    expect(component.isImportingGmail).toBeFalse();
    expect(eventServiceSpy.saveParsedEvent).not.toHaveBeenCalled();
    expect(dialogSpy.open).toHaveBeenCalled();
  });

  it('does not call the backend when no file is selected', () => {
    component.file = undefined;

    component.uploadGmailFile();

    expect(eventServiceSpy.parseEvent).not.toHaveBeenCalled();
    expect(dialogSpy.open).toHaveBeenCalled();
  });
});
