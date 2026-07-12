import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';
import { WebSocketService } from './services/websocket.service';

describe('App', () => {
  let webSocketServiceSpy: jasmine.SpyObj<WebSocketService>;

  beforeEach(async () => {
    webSocketServiceSpy = jasmine.createSpyObj<WebSocketService>('WebSocketService', [
      'connect',
      'disconnect',
    ]);

    localStorage.clear();

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        { provide: WebSocketService, useValue: webSocketServiceSpy },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the routed content', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('main.content')).toBeTruthy();
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });

  it('should hide the navigation when no user is logged in', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.side-bar')).toBeNull();
    expect(compiled.querySelector('main.content')?.classList).toContain('no-nav');
  });

  it('should not open a websocket connection without a token', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(webSocketServiceSpy.connect).not.toHaveBeenCalled();
  });
});
