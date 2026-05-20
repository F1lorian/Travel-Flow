import {Component, inject, Injectable, NgZone, OnInit} from '@angular/core';
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
import FullCalendar from "@fullcalendar/react";
import listPlugin from "@fullcalendar/list";

@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [FullCalendarModule, EventAddingManual],
  templateUrl: './calendar.html',
  styleUrl: './calendar.css',
})
export class CalendarComponent implements OnInit {
  showManualEventDialog = false;
  selectedDateStr = '';
  eventService = inject(EventService);

  calendarOptions: CalendarOptions = {
    plugins: [dayGridPlugin, interactionPlugin, timeGridPlugin, listPlugin],
    initialView: 'dayGridMonth',
    headerToolbar: {
      left: 'dayGridMonth timeGridWeek timeGridDay listYear',
      center: 'title',
      right: 'today prev next'
    },
    events: [],

    eventClick: this.handleEventClick.bind(this),
    dateClick: this.handleDateClick.bind(this)
  }

  constructor(private dialog: MatDialog, private ngZone: NgZone) {
  }

  ngOnInit(): void {
    //throw new Error("Method not implemented.");
    this.loadEvents();
  }


  handleEventClick(clickInfo: any) {

    const dialogRef = this.dialog.open(EventDialog, {
      data: {mode: 'create'},
      //panelClass: 'dialog-style'
    });
    //hier später nur noch auf den service verweisen, dass ein event geändert werden soll.
    //Dialog weiterhin hierüber


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
    this.ngZone.run(() => {
      this.selectedDateStr = clickInfo.dateStr;
      this.showManualEventDialog = true;
    });
    return;
  }

  loadEvents(){
    this.eventService.getEvents().subscribe(events => {
      this.calendarOptions.events = events;

    })
  }

}
