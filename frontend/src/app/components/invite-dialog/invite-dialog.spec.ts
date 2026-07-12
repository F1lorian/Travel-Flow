import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { InviteDialog } from './invite-dialog';
import { FriendsService } from '../../services/friends.service';

describe('InviteDialog', () => {
  let component: InviteDialog;
  let fixture: ComponentFixture<InviteDialog>;
  let friendsServiceSpy: jasmine.SpyObj<FriendsService>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<InviteDialog>>;

  beforeEach(async () => {
    friendsServiceSpy = jasmine.createSpyObj<FriendsService>('FriendsService', [
      'getFriends',
      'sendInviteMessage',
    ]);
    dialogRefSpy = jasmine.createSpyObj<MatDialogRef<InviteDialog>>('MatDialogRef', ['close']);

    friendsServiceSpy.getFriends.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [InviteDialog],
      providers: [
        { provide: FriendsService, useValue: friendsServiceSpy },
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: { tripId: 1 } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InviteDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load the friends of the current user', () => {
    expect(friendsServiceSpy.getFriends).toHaveBeenCalled();
    expect(component.friends).toEqual([]);
  });
});
