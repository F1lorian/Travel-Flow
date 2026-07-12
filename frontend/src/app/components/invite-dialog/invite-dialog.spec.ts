import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { InviteDialog } from './invite-dialog';

describe('InviteDialog', () => {
  let component: InviteDialog;
  let fixture: ComponentFixture<InviteDialog>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InviteDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // The dialog is normally opened through MatDialog, which provides these two.
        { provide: MatDialogRef, useValue: { close: jasmine.createSpy('close') } },
        { provide: MAT_DIALOG_DATA, useValue: { tripId: 1 } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(InviteDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
