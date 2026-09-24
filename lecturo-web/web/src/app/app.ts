import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { I18nService } from './core/i18n.service';
import { LayoutService } from './core/layout.service';
import { SessionService } from './core/session.service';
import { NAV_ITEMS } from './shell/nav';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly layout = inject(LayoutService);
  protected readonly session = inject(SessionService);
  protected readonly i18n = inject(I18nService);
  protected readonly nav = computed(() =>
    NAV_ITEMS.map((item) => ({ ...item, label: this.i18n.t(item.key) })),
  );
  protected readonly provider = 'DeepSeek';

  constructor() {
    void this.session.ensureLoaded();
  }
}
