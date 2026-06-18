import {Component, inject, Input, OnInit} from '@angular/core';
import {CalendarOptions, EventInput} from '@fullcalendar/core';
import { FullCalendarModule } from '@fullcalendar/angular';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import {EventDialog} from '../event-dialog/event-dialog';
import { MatDialog } from '@angular/material/dialog';
import { AddEventResponse } from '../../../event-adding-manual/models/AddEventResponse';
import { EventService} from '../../../event-adding-manual/service/event.service';
import timeGridPlugin from '@fullcalendar/timegrid';
import listPlugin from "@fullcalendar/list";
import { EventClickArg } from '@fullcalendar/core';
import { DateClickArg } from '@fullcalendar/interaction';
import {EditEventResponse} from '../../../event-adding-manual/models/EditEventResponse';
import { FullCalendarComponent } from '@fullcalendar/angular';
import { ViewChild } from '@angular/core';
import {EventImpl} from '@fullcalendar/core/internal';
import {EventTransferDialog} from '../event-transfer-dialog/event-transfer-dialog';
import {TripService} from '../../TripManagement/services/trip.service';
import { Router } from '@angular/router';
import {MessageDialog} from '../message-dialog/message-dialog';


@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [FullCalendarModule],
  templateUrl: './calendar.html',
  styleUrl: './calendar.css',
})
export class CalendarComponent implements OnInit {
  @ViewChild('calendar') calendarComponent!: FullCalendarComponent;
  @Input() selectedDate = '';
  selectedDateStr = '';
  eventService = inject(EventService);
  tripService = inject(TripService);
  calendarEvents: EventInput[] = [];
  router = inject(Router);
  private readonly dialog = inject(MatDialog);

  calendarOptions: CalendarOptions = {
    plugins: [dayGridPlugin, interactionPlugin, timeGridPlugin, listPlugin],
    initialView: 'dayGridMonth',
    dayMaxEvents: 4,
    contentHeight: 800,
    expandRows: false,
    fixedWeekCount: true,
    customButtons:{
      eventAddenButton:{
        text:'Add Event',
        click: () => {
          this.createEvent();
        }
      },
      eventImportButton:{
        text:'Import Event',
        click: () => {
          this.importEvent();
        }
      },
      eventExportButton:{
        text:'Export Event',
        click: () => {
          this.exportEvents();
        }
      }
    },
    headerToolbar: {
      left: 'dayGridMonth timeGridWeek timeGridDay listYear',
      center: 'title',
      right: 'today prev next'
    },
    footerToolbar:{
      left: 'eventAddenButton eventImportButton eventExportButton'
    },
    events: [],

    eventClick: this.handleEventClick.bind(this),
    dateClick: this.handleDateClick.bind(this)
  }

  ngOnInit(): void {
    this.loadEvents();
    this.loadTrips();
  }

  /**
   * Decides what happens if an event is clicked.
   * If the Event is a trip, it redirects the User to the trip site
   * If it is an Event, the Event opens as a dialog for the user
   *
   * @param clickInfo gives the event that is clicked by the user
   */
  handleEventClick(clickInfo: EventClickArg) {
    const selectedEvent = clickInfo.event;
    const type = clickInfo.event.extendedProps['type'];

    if (type === 'trip') {

      const tripId =
        selectedEvent.extendedProps['tripId'];

      this.router.navigate([
        '/trips',
        tripId
      ]);

      return;
    }

    const dialogRef = this.dialog.open(EventDialog, {
      maxHeight: '90vh',
      height: 'auto',
      data: {
        mode: 'show',
        id: selectedEvent.id,
        title: selectedEvent.title,
        kosten: selectedEvent.extendedProps['kosten'],
        tripId: selectedEvent.extendedProps['tripId'],
        start: selectedEvent.startStr.substring(0,16),
        end: selectedEvent.endStr.substring(0,16)
      }
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result?.action === 'edit') {
        this.editEvent(selectedEvent);
      }
    });
    return;
  }

  /**
   * Handles what happens when a date is clicked in the calendar
   * @param clickInfo is the clicked Date
   */
  handleDateClick(clickInfo: DateClickArg) {
    this.createEvent(clickInfo.dateStr);
  }

  /**
   * Adds the created event to the View without reloading everything from the Database
   * @param event is the created Event
   */
  addCreatedEvent(event: AddEventResponse) {
    const currentEvents = Array.isArray(this.calendarOptions.events)
      ? this.calendarOptions.events
      : [];
    this.calendarOptions = {
      ...this.calendarOptions,
      events: [
        ...currentEvents,
        {
          id: event.id,
          title: event.name,
          start: event.start,
          end: event.end,
          classNames: ['calendar-event'],
          extendedProps: {
            kosten: event.kosten,
            tripId: event.tripId,
          },
        },
      ],
    };
  }

  /**
   * Updates the created event in the View without reloading everything from the Database
   * @param id is the id from the updates event
   * @param event is the event with the new information
   */
  updateEditedEvent(id: string, event: EditEventResponse) {
    const calendarApi = this.calendarComponent.getApi();
    const calendarEvent = calendarApi.getEventById(id);

    if (!calendarEvent) {
      this.showMessage('Event im Kalender nicht gefunden');
      return;
    }

    calendarEvent.setProp('title', event.name);
    calendarEvent.setStart(event.start);
    calendarEvent.setEnd(event.end);
    calendarEvent.setExtendedProp('kosten', event.kosten);
    calendarEvent.setExtendedProp('tripId', event.tripId);
  }

  /**
   * Removes the deleted event from the View without reloading eerything from the database
   * @param id is the id from the deleted event
   */
  removeDeletedEvent(id: string) {
    const calendarApi = this.calendarComponent.getApi();
    const calendarEvent = calendarApi.getEventById(id);

    if (calendarEvent) {
      calendarEvent.remove();
    }
  }

  /**
   * opens a new dialog for creating an event
   * after creation, it gets added to the database via the eventService
   * @param selectedDateStr is the optional date which is used when the user creates an event by clicking on the date
   */
  createEvent(selectedDateStr?: string) {
    const dialogRef = this.dialog.open(EventDialog, {
      maxHeight: '90vh',
      height: 'auto',
      data: { mode: 'create', ...(selectedDateStr && { start: selectedDateStr + 'T00:00' }) }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result?.action === 'created') this.addCreatedEvent(result.event);
    });
  }


  /**
   * opens a new dialog for editing or deleting events
   * sends the updated information to the service, so that the information gets updated in the database, or the event gets deleted
   * @param selectedEvent
   */
  editEvent(selectedEvent: EventImpl){
    const dialogRef = this.dialog.open(EventDialog, {
      maxHeight: '90vh',
      height: 'auto',
      data: {
        mode: 'edit',
        id: selectedEvent.id,
        title: selectedEvent.title,
        kosten: selectedEvent.extendedProps['kosten'],
        tripId: selectedEvent.extendedProps['tripId'],
        start: selectedEvent.startStr.substring(0, 16),
        end: selectedEvent.endStr.substring(0, 16)}
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result?.action === 'saved') this.updateEditedEvent(selectedEvent.id, result.event);
      if (result?.action === 'deleted') this.removeDeletedEvent(selectedEvent.id);
    });
  }


  /**
   * opens the dialog for importing events via .eml files
   * after creation it gets added to the calendar view
   */
  importEvent(){
    const dialogRef = this.dialog.open(EventTransferDialog, {
      data: {mode: 'import',}
    })

    dialogRef.afterClosed().subscribe(result => {
      if(result){
        this.addCreatedEvent(result);
      }
    })
  }

  /**
   * opens the dialog for downloading the events as an .ics file
   */
  exportEvents(){
    this.dialog.open(EventTransferDialog, {
      data: {mode: 'export'}
    })
  }

  /**
   * loads the events from the database
   */
  loadEvents() {

    this.eventService.getEvents().subscribe({
      next: events => {

        const mappedEvents = events.map(event => ({
          id: event.id,
          title: event.title,
          start: event.start,
          end: event.end,

          classNames: ['calendar-event'],

          extendedProps: {
            type: 'event',
            kosten: event.kosten,
            tripId: event.tripId
          }
        }));

        this.calendarEvents = [
          ...this.calendarEvents,
          ...mappedEvents
        ];

        this.calendarOptions.events = this.calendarEvents;

      }
    });

  }

  /**
   * loads the trips from the database
   */
  loadTrips() {

    this.tripService.getAllTrips().subscribe({
      next: trips => {

        const mappedTrips = trips.map(trip => ({
          id: `trip-${trip.id}`,

          title: trip.name,

          start: trip.startDate,
          end: trip.endDate,

          editable: false,
          startEditable: false,
          durationEditable: false,

          classNames: ['trip-event'],

          extendedProps: {
            type: 'trip',
            tripId: trip.id
          }
        }));

        this.calendarEvents = [
          ...this.calendarEvents,
          ...mappedTrips
        ];

        this.calendarOptions.events = this.calendarEvents;

      }
    });

  }

  /**
   * shows a message for the user on the screen
   * @param message is the message that gets shown
   */
  showMessage(message: string){
    this.dialog.open(MessageDialog, {data: {message: message}})
  }

}
