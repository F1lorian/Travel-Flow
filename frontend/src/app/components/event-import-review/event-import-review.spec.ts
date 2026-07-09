import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { EventImportReview, ReviewItem } from './event-import-review';
import { ParsedEvent } from '../../../event-adding-manual/models/ParsedEvent';

describe('EventImportReview', () => {
  let component: EventImportReview;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<EventImportReview>>;

  const event = (name: string): ParsedEvent => ({
    name,
    kosten: 0,
    start: '2026-06-01T20:00:00',
    end: '2026-06-01T22:00:00',
  });

  const items: ReviewItem[] = [
    { subject: 'A', event: event('A') },
    { subject: 'B', event: event('B') },
  ];

  beforeEach(async () => {
    dialogRefSpy = jasmine.createSpyObj<MatDialogRef<EventImportReview>>('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [EventImportReview],
      providers: [
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: { items } },
      ],
    }).compileComponents();

    component = TestBed.createComponent(EventImportReview).componentInstance;
  });

  it('starts with every row selected', () => {
    expect(component.selectedCount).toBe(2);
  });

  it('confirm closes with only the selected events', () => {
    component.toggle(component.rows[0]);

    component.confirm();

    expect(component.selectedCount).toBe(1);
    expect(dialogRefSpy.close).toHaveBeenCalledWith([items[1].event]);
  });

  it('cancel closes with undefined', () => {
    component.cancel();

    expect(dialogRefSpy.close).toHaveBeenCalledWith(undefined);
  });
});
