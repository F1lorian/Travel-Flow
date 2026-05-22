import {Component, inject, Input, NgZone, OnInit} from '@angular/core';
import { CalendarOptions } from '@fullcalendar/core';
import { FullCalendarModule } from '@fullcalendar/angular';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import {EventDialog} from '../event-dialog/event-dialog';
import { MatDialog } from '@angular/material/dialog';
import { EventAddingManual } from '../../../event-adding-manual/event-adding-manual';
import { AddEventResponse } from '../../../event-adding-manual/models/AddEventResponse';
import { EventService} from '../../../event-adding-manual/service/event.service';
import timeGridPlugin from '@fullcalendar/timegrid';
import listPlugin from "@fullcalendar/list";
import {AddEventRequest} from '../../../event-adding-manual/models/AddEventRequest';
import {EditEventRequest} from '../../../event-adding-manual/models/EditEventRequest';


@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [FullCalendarModule, EventAddingManual],
  templateUrl: './calendar.html',
  styleUrl: './calendar.css',
})
export class CalendarComponent implements OnInit {
  @Input() selectedDate = '';
  showManualEventDialog = false;
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
        text:'Event hinzufügen'
      },
      eventImportButton:{
        text:'Event importieren'
      }
    },
    headerToolbar: {
      left: 'dayGridMonth timeGridWeek timeGridDay listYear',
      center: 'title',
      right: 'today prev next'
    },
    footerToolbar:{
      left: 'eventAddenButton eventImportButton'
    },
    events: [
      {title: "test", start:"2026-05-21", id:"1"}
    ],

    eventClick: this.handleEventClick.bind(this),
    dateClick: this.handleDateClick.bind(this)
  }

  constructor(private dialog: MatDialog, private ngZone: NgZone) {
  }

  ngOnInit(): void {
    this.loadEvents();
  }


  handleEventClick(clickInfo: any) {

    let selectedEvent = clickInfo.event;
    let id = selectedEvent.id;
    console.log("EventID =" + id);
    console.log(selectedEvent);
    console.log(selectedEvent.name + selectedEvent.kosten + selectedEvent.tripId + selectedEvent.start);
    const dialogRef = this.dialog.open(EventDialog, {
      data: {mode: 'edit', title: selectedEvent.name, kosten: selectedEvent.kosten, tripId: selectedEvent.tripId, start: selectedEvent.start},
    });

    dialogRef.afterClosed().subscribe(result => {
      if(result){
        if(result.action === 'save'){
          let updatedEvent: EditEventRequest = {
            id: id,
            kosten: result.kosten,
            name: result.name,
            start: '',
            tripId: result.tripId,
          }
          this.eventService.editEvent(updatedEvent);
          //.subscribe etc für rückmeldung
        }
        if(result.action === 'delete'){
          this.eventService.deleteEvent(id);
          //.subscribe für evtl rückmeldung
        }
      }
    })
    return;
    //hier noch mit dem service die änderungen an dem event durchführen

  }

  closeManualEventDialog() {
    this.showManualEventDialog = false;
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

  handleDateClick(clickInfo: any) {
    const dialogRef = this.dialog.open(EventDialog, {
      data:{mode: 'create'},
    })

    this.selectedDateStr = clickInfo.dateStr;
    dialogRef.afterClosed().subscribe(result => {

      if(result) {
        let newEvent: AddEventRequest = {
          name: result.title,
          kosten: result.kosten,
          tripId: result.tripid,
          start: this.selectedDateStr +"T00:00:00",
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

  loadEvents(){
    this.eventService.getEvents().subscribe(events => {
      this.calendarOptions.events = events;

    })
  }

}
