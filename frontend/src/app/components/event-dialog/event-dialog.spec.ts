import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { of } from 'rxjs';

import { EventDialog } from './event-dialog';
import { EventService } from '../../../event-adding-manual/service/event.service';
import { TripService } from '../../TripManagement/services/trip.service';
import { FriendsService } from '../../services/friends.service';

describe('EventDialog', () => {
  let component: EventDialog;
  let fixture: ComponentFixture<EventDialog>;

  beforeEach(async () => {
    const tripServiceSpy = jasmine.createSpyObj<TripService>('TripService', ['getAllTrips']);
    tripServiceSpy.getAllTrips.and.returnValue(of([]));

    const friendsServiceSpy = jasmine.createSpyObj<FriendsService>('FriendsService', ['getFriends']);
    friendsServiceSpy.getFriends.and.returnValue(of([]));

    const eventServiceSpy = jasmine.createSpyObj<EventService>('EventService', [
      'getConflictEvents',
      'createManualEvent',
      'editEvent',
      'deleteEvent',
      'getParticipants',
    ]);

    await TestBed.configureTestingModule({
      imports: [EventDialog],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { mode: 'create' } },
        { provide: MatDialogRef, useValue: { close: () => {} } },
        { provide: EventService, useValue: eventServiceSpy },
        { provide: TripService, useValue: tripServiceSpy },
        { provide: FriendsService, useValue: friendsServiceSpy },
        { provide: MatDialog, useValue: {} },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EventDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
