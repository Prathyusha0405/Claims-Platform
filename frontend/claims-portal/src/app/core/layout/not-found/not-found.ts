import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { AuthStore } from '@core/services/auth-store';
import { roleHome } from '@core/services/role-home';

/** The catch-all page, outside every layout so it works whether or not the visitor is signed in. */
@Component({
  imports: [MatButtonModule, MatIconModule, RouterLink],
  selector: 'app-not-found',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './not-found.scss',
  templateUrl: './not-found.html',
})
export class NotFound {
  private readonly auth = inject(AuthStore);

  protected readonly signedIn = this.auth.isAuthenticated;
  protected readonly home = computed(() => {
    const role = this.auth.role();
    return role === null ? '/auth/login' : roleHome(role);
  });
}
