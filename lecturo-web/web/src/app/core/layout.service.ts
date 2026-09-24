import { Injectable, computed, signal } from '@angular/core';

export type WindowClass = 'compact' | 'medium' | 'expanded';

/** Material 3 window size classes: compact <600, medium 600–1023, expanded ≥1024. */
@Injectable({ providedIn: 'root' })
export class LayoutService {
  private readonly width = signal<number>(typeof window === 'undefined' ? 1440 : window.innerWidth);

  readonly windowClass = computed<WindowClass>(() => {
    const w = this.width();
    return w >= 1024 ? 'expanded' : w >= 600 ? 'medium' : 'compact';
  });

  readonly isCompact = computed(() => this.windowClass() === 'compact');
  readonly isExpanded = computed(() => this.windowClass() === 'expanded');

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', () => this.width.set(window.innerWidth));
    }
  }
}
