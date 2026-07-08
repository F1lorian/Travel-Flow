import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { EventPreviewDialog } from './event-preview-dialog';

describe('EventPreviewDialog', () => {
  let component: EventPreviewDialog;
  let fixture: ComponentFixture<EventPreviewDialog>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<EventPreviewDialog>>;

  const parsedEvent = {
    name: 'Konzert',
    kosten: 49,
    start: '2026-06-01T20:00:00',
    end: '2026-06-01T22:00:00',
  };

  beforeEach(async () => {
    dialogRefSpy = jasmine.createSpyObj<MatDialogRef<EventPreviewDialog>>('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [EventPreviewDialog],
      providers: [
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: { parsedEvent } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EventPreviewDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should prefill the form with the parsed values', () => {
    expect(component.name).toBe('Konzert');
    expect(component.kosten).toBe(49);
    expect(component.start).toBe('2026-06-01T20:00');
    expect(component.end).toBe('2026-06-01T22:00');
  });

  it('should close with the edited values when confirmed', () => {
    component.name = 'Anderes Konzert';
    component.kosten = 60;
    component.start = '2026-06-02T18:30';
    component.end = '2026-06-02T21:00';

    component.confirm();

    expect(dialogRefSpy.close).toHaveBeenCalledWith({
      name: 'Anderes Konzert',
      kosten: 60,
      start: '2026-06-02T18:30:00',
      end: '2026-06-02T21:00:00',
    });
  });

  it('should close without a value when cancelled', () => {
    component.cancel();

    expect(dialogRefSpy.close).toHaveBeenCalledWith();
  });
});
