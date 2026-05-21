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
    events: [],

    eventClick: this.handleEventClick.bind(this),
    dateClick: this.handleDateClick.bind(this)
  }

  constructor(private dialog: MatDialog, private ngZone: NgZone) {
  }

  ngOnInit(): void {
    this.loadEvents();
  }


  handleEventClick(clickInfo: any) {

    const dialogRef = this.dialog.open(EventDialog, {
      data: {mode: 'edit'},
    });

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
