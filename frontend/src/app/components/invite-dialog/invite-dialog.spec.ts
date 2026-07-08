import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { of } from 'rxjs';

import { InviteDialog } from './invite-dialog';
import { FriendsService } from '../../services/friends.service';

describe('InviteDialog', () => {
  let component: InviteDialog;
  let fixture: ComponentFixture<InviteDialog>;

  beforeEach(async () => {
    const friendsServiceSpy = jasmine.createSpyObj<FriendsService>('FriendsService', ['getFriends']);
    friendsServiceSpy.getFriends.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [InviteDialog],
      providers: [
        { provide: FriendsService, useValue: friendsServiceSpy },
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
});
