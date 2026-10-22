import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Application root: hosts the router outlet that every layout and page renders into. */
@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {}
