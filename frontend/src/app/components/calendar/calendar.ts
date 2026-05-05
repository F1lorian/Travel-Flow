import {Component, OnInit} from '@angular/core';
import { CalendarOptions } from '@fullcalendar/core';
import { FullCalendarModule } from '@fullcalendar/angular';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import {EventDialog} from '../event-dialog/event-dialog';
import { MatDialog } from '@angular/material/dialog';


@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [FullCalendarModule],
  templateUrl: './calendar.html',
  styleUrl: './calendar.css',
})
export class CalendarComponent implements OnInit {
  calendarOptions: CalendarOptions = {
    plugins: [dayGridPlugin, interactionPlugin],
    initialView: 'dayGridMonth',
    events: [
      { title: 'test', start: '2026-05-05' },
      { title: 'sprint 1', start: '2026-04-28', end: '2026-05-08'}
    ],

    eventClick: this.handleEventClick.bind(this),
    dateClick: this.handleDateClick.bind(this)
  }

  constructor(private dialog: MatDialog) {}


  handleEventClick(clickInfo: any) {
    const title = clickInfo.event.title;
    alert(`Ihr Event ist: ${title}`);
  }

  handleDateClick(clickInfo: any) {
    const dialogRef = this.dialog.open(EventDialog);

    dialogRef.afterClosed().subscribe(title => {

      console.log("Returned:", title);

      if(title) {
        clickInfo.view.calendar.addEvent({
          title: title,
          start: clickInfo.dateStr
        });

        //hier noch in db speichern!

      }
    })

  }

  ngOnInit() {
    //hier events aus db laden!

  }




}
