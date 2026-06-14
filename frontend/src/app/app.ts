import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private router = inject(Router);
  protected readonly title = signal('sep');

  get showNavigation(): boolean {
    return this.router.url !== '/account' && Boolean(localStorage.getItem('sepToken'));
  }

  logout(): void {
    localStorage.removeItem('sepUser');
    localStorage.removeItem('sepToken');
    this.router.navigateByUrl('/account');
  }
}
