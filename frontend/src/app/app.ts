import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { ProfileMenu } from './profile-management/profile-menu/profile-menu';
import { NotificationMenu } from './components/notification-menu/notification-menu';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, ProfileMenu, NotificationMenu],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private router = inject(Router);

  protected readonly title = signal('sep');
  protected readonly sidebarOpen = signal(false);

  get showNavigation(): boolean {
    return this.router.url !== '/account' && Boolean(localStorage.getItem('sepToken'));
  }

  toggleSidebar(): void {
    this.sidebarOpen.update(open => !open);
  }

  closeSidebar(): void {
    this.sidebarOpen.set(false);
  }

  logout(): void {
    this.closeSidebar();
    localStorage.removeItem('sepUser');
    localStorage.removeItem('sepToken');
    this.router.navigateByUrl('/account');
  }
}
