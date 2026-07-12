import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { EventDialog } from './event-dialog';
import { EventService } from '../../../event-adding-manual/service/event.service';
import { TripService } from '../../TripManagement/services/trip.service';
import { FriendsService } from '../../services/friends.service';

describe('EventDialog', () => {
  let component: EventDialog;
  let fixture: ComponentFixture<EventDialog>;
  let eventServiceSpy: jasmine.SpyObj<EventService>;
  let tripServiceSpy: jasmine.SpyObj<TripService>;
  let friendsServiceSpy: jasmine.SpyObj<FriendsService>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<EventDialog>>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  beforeEach(async () => {
    eventServiceSpy = jasmine.createSpyObj<EventService>('EventService', [
      'getConflictEvents',
      'createManualEvent',
      'editEvent',
      'deleteEvent',
      'inviteFriendToEvent',
    ]);
    tripServiceSpy = jasmine.createSpyObj<TripService>('TripService', ['getAllTrips']);
    friendsServiceSpy = jasmine.createSpyObj<FriendsService>('FriendsService', ['getFriends']);
    dialogRefSpy = jasmine.createSpyObj<MatDialogRef<EventDialog>>('MatDialogRef', ['close']);
    dialogSpy = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    tripServiceSpy.getAllTrips.and.returnValue(of([]));
    friendsServiceSpy.getFriends.and.returnValue(of([]));
    dialogSpy.open.and.returnValue({ afterClosed: () => of(true) } as never);

    await TestBed.configureTestingModule({
      imports: [EventDialog],
      providers: [
        { provide: EventService, useValue: eventServiceSpy },
        { provide: TripService, useValue: tripServiceSpy },
        { provide: FriendsService, useValue: friendsServiceSpy },
        { provide: MatDialog, useValue: dialogSpy },
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: { mode: 'create' } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EventDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load trips and friends on init', () => {
    expect(tripServiceSpy.getAllTrips).toHaveBeenCalled();
    expect(friendsServiceSpy.getFriends).toHaveBeenCalled();
  });
});
