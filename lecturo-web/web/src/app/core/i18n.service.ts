import { Injectable, signal } from '@angular/core';

export type Locale = 'en' | 'id';

/**
 * Runtime strings. English ships now; Indonesian is one dictionary away and can
 * be selected without a rebuild. (Angular's built-in i18n is build-time only,
 * which does not suit a user-switchable locale.)
 */
const DICTIONARIES: Record<Locale, Record<string, string>> = {
  en: {
    'nav.library': 'Library',
    'nav.saved': 'Saved',
    'nav.chats': 'Chats',
    'nav.settings': 'Settings',
    'app.tag': 'Read · Save · Ask',
    'provider.label': 'CHAT PROVIDER',
  },
  id: {
    'nav.library': 'Perpustakaan',
    'nav.saved': 'Tersimpan',
    'nav.chats': 'Obrolan',
    'nav.settings': 'Pengaturan',
    'app.tag': 'Baca · Simpan · Tanya',
    'provider.label': 'PENYEDIA OBROLAN',
  },
};

@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly current = signal<Locale>(detectLocale());
  readonly locale = this.current.asReadonly();

  t(key: string): string {
    return DICTIONARIES[this.current()][key] ?? DICTIONARIES.en[key] ?? key;
  }

  setLocale(locale: Locale): void {
    this.current.set(locale);
    try {
      localStorage.setItem('lecturo.locale', locale);
    } catch {
      /* private mode */
    }
  }
}

function detectLocale(): Locale {
  try {
    const stored = localStorage.getItem('lecturo.locale');
    if (stored === 'en' || stored === 'id') return stored;
  } catch {
    /* ignore */
  }
  const browser = typeof navigator !== 'undefined' ? navigator.language.toLowerCase() : 'en';
  return browser.startsWith('id') ? 'id' : 'en';
}
