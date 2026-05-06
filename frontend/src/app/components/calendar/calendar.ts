import {Component, NgZone, OnInit} from '@angular/core';
import { CalendarOptions } from '@fullcalendar/core';
import { FullCalendarModule } from '@fullcalendar/angular';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import {EventDialog} from '../event-dialog/event-dialog';
import { MatDialog } from '@angular/material/dialog';
import { EventAddingManual } from '../../../event-adding-manual/event-adding-manual';

@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [FullCalendarModule, EventAddingManual],
  templateUrl: './calendar.html',
  styleUrl: './calendar.css',
})
export class CalendarComponent implements OnInit {
  showManualEventDialog = false;

  calendarOptions: CalendarOptions = {
    plugins: [dayGridPlugin, interactionPlugin],
    initialView: 'dayGridMonth',
    events: [
      { title: '1', start: '2026-04-27'},
      { title: 'test', start: '2026-05-05'},
      { title: 'sprint 1', start: '2026-04-28', end: '2026-05-08'}
    ],

    eventClick: this.handleEventClick.bind(this),
    dateClick: this.handleDateClick.bind(this)
  }

  constructor(private dialog: MatDialog, private ngZone: NgZone) {}


  handleEventClick(clickInfo: any) {
    //hier später nur noch auf den service verweisen, dass ein event geändert werden soll.
    //Dialog weiterhin hierüber


  }

  closeManualEventDialog() {
    this.showManualEventDialog = false;
  }

  handleDateClick(clickInfo: any) {
    this.ngZone.run(() => {
      this.showManualEventDialog = true;
    });
    return;

    const dialogRef = this.dialog.open(EventDialog, {
      data: {mode: 'create'},
      panelClass: 'dialog-style'
    });

    dialogRef.afterClosed().subscribe(result => {

      if(!result) return;

      if(result) {
        clickInfo.view.calendar.addEvent({
          title: result.title,
          start: clickInfo.dateStr
        });

        //hier noch in db speichern über den service!
        //methode nur noch für die kommunikation über den dalog da


      }
    })

  }

  ngOnInit() {
    //hier events aus db laden!

  }




}
