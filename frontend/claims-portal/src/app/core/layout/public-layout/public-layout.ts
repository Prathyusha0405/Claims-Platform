import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterOutlet } from '@angular/router';

/** Frame around the pages anonymous visitors see (login, register): a brand panel beside the page's own card. */
@Component({
  imports: [MatIconModule, RouterOutlet],
  selector: 'app-public-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './public-layout.scss',
  templateUrl: './public-layout.html',
})
export class PublicLayout {}
