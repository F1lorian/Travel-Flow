import {Component, NgModule, signal} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {CalendarComponent} from './components/calendar/calendar';
import {EventDialog} from './components/event-dialog/event-dialog';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CalendarComponent, EventDialog],
  templateUrl: './app.html',
  styleUrl: './app.css'
})

export class App {
  protected readonly title = signal('sep');

}
