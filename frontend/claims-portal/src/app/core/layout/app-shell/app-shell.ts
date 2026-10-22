import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { AuthStore } from '@core/services/auth-store';
import { roleHome } from '@core/services/role-home';
import { navItemsFor } from '../nav-config';
import { NotificationBell } from '../notification-bell/notification-bell';
import { UserMenu } from '../user-menu/user-menu';

/** Below this width the navigation becomes a slide-over drawer opened from the toolbar. */
const COMPACT_LAYOUT = '(max-width: 899.98px)';

/** Frame around every signed-in page: toolbar, role-filtered side navigation and the page itself. */
@Component({
  imports: [
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatSidenavModule,
    MatToolbarModule,
    NotificationBell,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    UserMenu,
  ],
  selector: 'app-app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './app-shell.scss',
  templateUrl: './app-shell.html',
})
export class AppShell {
  private readonly auth = inject(AuthStore);
  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');

  protected readonly navItems = computed(() => navItemsFor(this.auth.role()));
  protected readonly home = computed(() => {
    const role = this.auth.role();
    return role === null ? '/' : roleHome(role);
  });
  protected readonly compact = toSignal(
    inject(BreakpointObserver)
      .observe(COMPACT_LAYOUT)
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  /**
   * "Skip to main content" for keyboard and screen-reader users. A plain `href="#..."` would resolve against the
   * document's <base href="/"> and navigate the router to the home page, so focus is moved by hand.
   */
  protected skipToContent(event: Event): void {
    event.preventDefault();
    this.main().nativeElement.focus();
  }
}
