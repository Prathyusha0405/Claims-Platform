import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { ROLE_LABELS } from '@core/models/role';
import { AuthStore } from '@core/services/auth-store';
import { roleHome } from '@core/services/role-home';

/** Shown inside the shell when a signed-in user opens an area their role may not use. */
@Component({
  imports: [MatButtonModule, MatIconModule, RouterLink],
  selector: 'app-forbidden',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './forbidden.scss',
  templateUrl: './forbidden.html',
})
export class Forbidden {
  private readonly auth = inject(AuthStore);

  protected readonly roleLabel = computed(() => {
    const role = this.auth.role();
    return role === null ? null : ROLE_LABELS[role];
  });
  protected readonly home = computed(() => {
    const role = this.auth.role();
    return role === null ? '/' : roleHome(role);
  });
}
