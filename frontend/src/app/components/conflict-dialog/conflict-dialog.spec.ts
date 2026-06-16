import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { ConflictDialog } from './conflict-dialog';

describe('ConflictDialog', () => {
  let component: ConflictDialog;
  let fixture: ComponentFixture<ConflictDialog>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<ConflictDialog>>;

  const conflicts = [
    {
      id: '1',
      name: 'Konzert',
      kosten: 49,
      tripId: 1,
      start: '2026-06-01T20:00:00',
      end: '2026-06-01T22:00:00',
    },
  ];

  beforeEach(async () => {
    dialogRefSpy = jasmine.createSpyObj<MatDialogRef<ConflictDialog>>('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [ConflictDialog],
      providers: [
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: { conflicts } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ConflictDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should use conflicts from dialog data', () => {
    expect(component.conflicts).toEqual(conflicts);
  });

  it('should close with true when confirmed', () => {
    component.confirm();

    expect(dialogRefSpy.close).toHaveBeenCalledWith(true);
  });

  it('should close with false when cancelled', () => {
    component.cancel();

    expect(dialogRefSpy.close).toHaveBeenCalledWith(false);
  });
});
