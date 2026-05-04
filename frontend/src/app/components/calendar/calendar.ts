import {Component, OnInit} from '@angular/core';
import { CalendarOptions } from '@fullcalendar/core';
import { FullCalendarModule } from '@fullcalendar/angular';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';

@Component({
  selector: 'app-calendar',
  imports: [FullCalendarModule],
  templateUrl: './calendar.html',
  styleUrl: './calendar.css',
})
export class CalendarComponent implements OnInit {
  calendarOptions: CalendarOptions = {
    plugins: [dayGridPlugin, interactionPlugin],
    initialView: 'dayGridMonth',
    events: [
      { title: 'test', date: '2026-05-05' },
      { title: 'sprint 1', start: '2026-04-28', end: '2026-05-08'}
    ],

    eventClick: this.handleEventClick.bind(this),
    dateClick: this.handleDateClick.bind(this)
  }

  handleEventClick(clickInfo: any) {
    const title = clickInfo.event.title;
    alert(`Ihr Event ist: ${title}`);
  }

  handleDateClick(clickInfo: any) {
    const title = prompt('Event Titel eingeben:');

    if (title) {
      clickInfo.view.calendar.addEvent({
        title: title,
        date: clickInfo.dateStr
      });
    }
  }

  ngOnInit() {

  }




}
