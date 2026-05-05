import {Component, signal} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {CalendarComponent} from './components/calendar/calendar';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CalendarComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})

export class App {
  protected readonly title = signal('sep');

}
