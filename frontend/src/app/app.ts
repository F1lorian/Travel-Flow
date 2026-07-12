import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { ProfileMenu } from './profile-management/profile-menu/profile-menu';
import { NotificationMenu } from './components/notification-menu/notification-menu';
import { WebSocketService } from './services/websocket.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, ProfileMenu, NotificationMenu],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit{
  private router = inject(Router);
  private readonly webSocketService = inject(WebSocketService);

  protected readonly title = signal('sep');
  protected readonly sidebarOpen = signal(false);

  ngOnInit(): void {
    if (localStorage.getItem('sepToken')) {
      this.webSocketService.connect();
    }
  }

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
