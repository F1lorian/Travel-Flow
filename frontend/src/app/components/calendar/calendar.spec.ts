import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { CalendarComponent } from './calendar';
import { EventService } from '../../../event-adding-manual/service/event.service';
import { TripService } from '../../TripManagement/services/trip.service';
import { FriendsService } from '../../services/friends.service';

describe('Calendar', () => {
  let component: CalendarComponent;
  let fixture: ComponentFixture<CalendarComponent>;

  beforeEach(async () => {
    const eventServiceSpy = jasmine.createSpyObj<EventService>('EventService', ['getEvents']);
    eventServiceSpy.getEvents.and.returnValue(of([]));

    const tripServiceSpy = jasmine.createSpyObj<TripService>('TripService', ['getAllTrips']);
    tripServiceSpy.getAllTrips.and.returnValue(of([]));

    const friendsServiceSpy = jasmine.createSpyObj<FriendsService>('FriendsService', ['getFriends']);
    friendsServiceSpy.getFriends.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [CalendarComponent],
      providers: [
        provideRouter([]),
        { provide: EventService, useValue: eventServiceSpy },
        { provide: TripService, useValue: tripServiceSpy },
        { provide: FriendsService, useValue: friendsServiceSpy },
        { provide: MatDialog, useValue: {} },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CalendarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
