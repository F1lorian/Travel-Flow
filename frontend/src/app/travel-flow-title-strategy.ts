import { inject, Injectable } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

/**
 * Sets the browser tab title to "Travel Flow - <page>" based on the route's
 * `title` property. Routes without a title (e.g. the login page) fall back
 * to plain "Travel Flow".
 */
@Injectable({ providedIn: 'root' })
export class TravelFlowTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const pageTitle = this.buildTitle(snapshot);
    this.title.setTitle(pageTitle ? `Travel Flow - ${pageTitle}` : 'Travel Flow');
  }
}
