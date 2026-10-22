import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';

import { MyAssignedClaims } from './my-assigned-claims/my-assigned-claims';
import { UnassignedQueue } from './unassigned-queue/unassigned-queue';

/** The officer's home: claims waiting for someone to take them, and the claims this officer already holds. */
@Component({
  imports: [MatTabsModule, MyAssignedClaims, UnassignedQueue],
  selector: 'app-work-queue',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './work-queue.scss',
  templateUrl: './work-queue.html',
})
export class WorkQueue {}
