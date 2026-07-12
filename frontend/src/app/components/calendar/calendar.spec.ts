import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { CalendarComponent } from './calendar';
import { EventService } from '../../../event-adding-manual/service/event.service';
import { TripService } from '../../TripManagement/services/trip.service';
import { FriendsService } from '../../services/friends.service';

describe('CalendarComponent', () => {
  let component: CalendarComponent;
  let fixture: ComponentFixture<CalendarComponent>;
  let eventServiceSpy: jasmine.SpyObj<EventService>;
  let tripServiceSpy: jasmine.SpyObj<TripService>;
  let friendsServiceSpy: jasmine.SpyObj<FriendsService>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  beforeEach(async () => {
    eventServiceSpy = jasmine.createSpyObj<EventService>('EventService', ['getEvents']);
    tripServiceSpy = jasmine.createSpyObj<TripService>('TripService', ['getAllTrips']);
    friendsServiceSpy = jasmine.createSpyObj<FriendsService>('FriendsService', ['getFriends']);
    dialogSpy = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    eventServiceSpy.getEvents.and.returnValue(of([]));
    tripServiceSpy.getAllTrips.and.returnValue(of([]));
    friendsServiceSpy.getFriends.and.returnValue(of([]));
    dialogSpy.open.and.returnValue({ afterClosed: () => of(undefined) } as never);

    await TestBed.configureTestingModule({
      imports: [CalendarComponent],
      providers: [
        provideRouter([]),
        { provide: EventService, useValue: eventServiceSpy },
        { provide: TripService, useValue: tripServiceSpy },
        { provide: FriendsService, useValue: friendsServiceSpy },
        { provide: MatDialog, useValue: dialogSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CalendarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load events and trips on init', () => {
    expect(eventServiceSpy.getEvents).toHaveBeenCalled();
    expect(tripServiceSpy.getAllTrips).toHaveBeenCalled();
  });
});
