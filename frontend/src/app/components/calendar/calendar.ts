import {Component, inject, Input, NgZone, OnInit} from '@angular/core';
import { CalendarOptions } from '@fullcalendar/core';
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
        text:'Event importieren'
      },
      eventExportButton:{
        text:'Events exportieren'
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

  ngOnInit(): void {
    this.loadEvents();
  }


  handleEventClick(clickInfo: EventClickArg) {
    let selectedEvent = clickInfo.event;
    const dialogRef = this.dialog.open(EventDialog, {
      data: {
        mode: 'show',
        id: selectedEvent.id,
        title: selectedEvent.title,
        kosten: selectedEvent.extendedProps['kosten'],
        tripId: selectedEvent.extendedProps['trip'],
        start: selectedEvent.startStr
      }
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result?.action === 'edit') {
        this.editEvent(selectedEvent);
      }
    });
    return;
  }

  handleDateClick(clickInfo: any) {
    this.createEvent(clickInfo.dateStr);
  }

  addCreatedEvent(event: AddEventResponse) {
    const currentEvents = Array.isArray(this.calendarOptions.events)
      ? this.calendarOptions.events
      : [];
    const startDate = this.selectedDateStr || event.uhrzeit.substring(0, 10);

    this.calendarOptions = {
      ...this.calendarOptions,
      events: [
        ...currentEvents,
        {
          title: event.name,
          start: startDate,
        },
      ],
    };
  }

  updateEditedEvent(event: EditEventResponse) {
    const calendarApi = this.calendarComponent.getApi();
    const calendarEvent = calendarApi.getEventById(event.id);

    if (!calendarEvent) {
      console.error('Event im Kalender nicht gefunden:', event.id);
      return;
    }

    calendarEvent.setProp('title', event.name);
    calendarEvent.setStart(event.start);
    calendarEvent.setExtendedProp('kosten', event.kosten);
    calendarEvent.setExtendedProp('trip', event.tripId);
  }

  removeDeletedEvent(id: string) {
    const calendarApi = this.calendarComponent.getApi();
    const calendarEvent = calendarApi.getEventById(id);

    if (calendarEvent) {
      calendarEvent.remove();
    }
  }

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
          tripId: result.tripId,
          start: result.start,
        }
        console.log(newEvent);
        this.eventService.createManualEvent(newEvent)
          .subscribe({
            next: (response) => {
              console.log("Event Response:", response);
              this.addCreatedEvent(response);
            },
            error: () => {
              console.error("Event konnte nicht erstellt werden!");
            }
          });
      }
    })
    return;
  }

  editEvent(selectedEvent: EventImpl){
    let id = selectedEvent.id;
    const dialogRef = this.dialog.open(EventDialog, {
      data: {mode: 'edit',
        title: selectedEvent.title,
        kosten: selectedEvent.extendedProps['kosten'],
        tripId: selectedEvent.extendedProps['trip'],
        start: selectedEvent.startStr + "T00:00"}, //noch ändern, wenn uhrzeiten hinzugefügt werden
    });
    dialogRef.afterClosed().subscribe(result => {
      if(result){
        if(result.action === 'save'){
          let updatedEvent: EditEventRequest = {
            id: id,
            kosten: result.kosten,
            name: result.title,
            start: result.start,
            tripId: result.tripId,
          }
          console.log(updatedEvent);
          this.eventService.editEvent(updatedEvent).subscribe({
            next:(response: EditEventResponse) => {
              console.log(response);
              this.updateEditedEvent(response);
            },
            error: () => {
              console.error("Event konnte nicht geändert werden!");
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
              console.error("Event konnte nicht gelöscht werden!");
            }
          })
        }
      }
    })
    return;
  }

  loadEvents(){
    this.eventService.getEvents().subscribe(events => {
      this.calendarOptions.events = events;
    })
  }

}
