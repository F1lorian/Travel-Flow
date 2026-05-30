import {Component, inject, Input, NgZone, OnInit} from '@angular/core';
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
import {AddEventRequest} from '../../../event-adding-manual/models/AddEventRequest';
import {EditEventRequest} from '../../../event-adding-manual/models/EditEventRequest';
import { EventClickArg } from '@fullcalendar/core';
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

  calendarOptions: CalendarOptions = {
    plugins: [dayGridPlugin, interactionPlugin, timeGridPlugin, listPlugin],
    initialView: 'dayGridMonth',
    dayMaxEvents: 4,
    contentHeight: 800,
    expandRows: false,
    fixedWeekCount: true,
    customButtons:{
      eventAddenButton:{
        text:'Event hinzufügen',
        click: () => {
          this.createEvent();
        }
      },
      eventImportButton:{
        text:'Event importieren',
        click: () => {
          this.importEvent();
        }
      },
      eventExportButton:{
        text:'Events exportieren',
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

  constructor(private dialog: MatDialog, private ngZone: NgZone) {
  }

  //loads all the events and trips on init
  ngOnInit(): void {
    this.loadEvents();
    this.loadTrips();
  }

  //handles what happens when a calendar event is clickes
  //if it is a trip, the user gets directed to the trip page, if it is an event, the event opens as a dialog for the user
  handleEventClick(clickInfo: EventClickArg) {
    let selectedEvent = clickInfo.event;
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

  //handles what happens when a calendar date is clicked, meaning a new event gets created
  handleDateClick(clickInfo: any) {
    this.createEvent(clickInfo.dateStr);
  }

  //adds the created event from the backend to the calendar view without reloading the entire database
  addCreatedEvent(event: AddEventResponse, tripId?: number) {
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

  //updates the edited event in the view with the new information without reloading the entire database
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

  //removes the event from the view after it got deleted
  removeDeletedEvent(id: string) {
    const calendarApi = this.calendarComponent.getApi();
    const calendarEvent = calendarApi.getEventById(id);

    if (calendarEvent) {
      calendarEvent.remove();
    }
  }

  //opens a new dialog for creating events and sends the created event to the service, so that it gets saved in the database
  createEvent(selectedDateStr?: string) {
    const dialogRef = this.dialog.open(EventDialog, {
      data:{mode: 'create',
        ...(selectedDateStr && { start: selectedDateStr + 'T00:00' }),},
    })

    dialogRef.afterClosed().subscribe(result => {
      if(result) {
        let newEvent: AddEventRequest = {
          name: result.title,
          kosten: result.kosten,
          tripId: result.tripId ?? undefined,
          start: result.start,
          end: result.end,
        }
        console.log(newEvent);
        this.eventService.createManualEvent(newEvent)
          .subscribe({
            next: (response) => {
              console.log("Event Response:", response);
              this.addCreatedEvent(response, newEvent.tripId);
            },
            error: (err) => {
              console.error(err);
              this.showMessage(
                err.error || "Event konnte nicht erstellt werden!"
              );
            }
          });
      }
    })
    return;
  }

  //opens a new dialog for editing or deleting events and sends the updated information to the service, so that the information gets updated in the database, or the event gets deleted
  editEvent(selectedEvent: EventImpl){
    let id = selectedEvent.id;
    const dialogRef = this.dialog.open(EventDialog, {
      data: {mode: 'edit',
        title: selectedEvent.title,
        kosten: selectedEvent.extendedProps['kosten'],
        tripId: selectedEvent.extendedProps['tripId'],
        start: selectedEvent.startStr.substring(0,16),
        end: selectedEvent.endStr.substring(0,16)
      },
    });
    dialogRef.afterClosed().subscribe(result => {
      if(result){
        console.log("result")
        console.log(result)
        if(result.action === 'save'){
          let updatedEvent: EditEventRequest = {
            id: id,
            kosten: result.kosten,
            name: result.title,
            start: result.start,
            end: result.end,
            tripId: result.tripId,
          }
          console.log("Updated Event:");
          console.log(updatedEvent);
          this.eventService.editEvent(updatedEvent).subscribe({
            next:(response: EditEventResponse) => {
              console.log(response);
              this.updateEditedEvent(id, response);
            },
            error: () => {
              this.showMessage("Event konnte nicht geändert werden!");
            }
          })
        }
        if(result.action === 'delete'){
          this.eventService.deleteEvent(id).subscribe({
            next:(response: string) => {
              console.log(response);
              this.removeDeletedEvent(id)
            },
            error: () => {
              this.showMessage("Event konnte nicht gelöscht werden!");
            }
          })
        }
      }
    })
    return;
  }

  //opens the dialog for importing events via .eml files
  //after creation it gets added to the calendar view
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

  //opens the dialog for downloading the events as an .ics file
  exportEvents(){
    this.dialog.open(EventTransferDialog, {
      data: {mode: 'export'}
    })
  }

  //loads all events from the database
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

  //loads all trips from the database
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

  //shows a message for the User on the screen
  showMessage(message: string){
    this.dialog.open(MessageDialog, {data: {message: message}})
  }

}
