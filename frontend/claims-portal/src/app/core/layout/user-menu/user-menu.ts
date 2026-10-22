import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import { ROLE_LABELS } from '@core/models/role';
import { AuthStore } from '@core/services/auth-store';

/** The signed-in user's name in the toolbar, with a menu showing who they are and a way to sign out. */
@Component({
  imports: [MatButtonModule, MatIconModule, MatMenuModule],
  selector: 'app-user-menu',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './user-menu.scss',
  templateUrl: './user-menu.html',
})
export class UserMenu {
  private readonly auth = inject(AuthStore);

  protected readonly user = this.auth.user;
  protected readonly roleLabel = computed(() => {
    const role = this.auth.role();
    return role === null ? '' : ROLE_LABELS[role];
  });

  protected signOut(): void {
    this.auth.signOut('user');
  }
}
