import { TestBed } from '@angular/core/testing';
import { I18nService } from './i18n.service';

describe('I18nService', () => {
  it('translates and switches locale at runtime', () => {
    const service = TestBed.inject(I18nService);

    service.setLocale('en');
    expect(service.t('nav.library')).toBe('Library');

    service.setLocale('id');
    expect(service.t('nav.library')).toBe('Perpustakaan');
  });

  it('falls back to the key for unknown strings', () => {
    const service = TestBed.inject(I18nService);
    expect(service.t('missing.key')).toBe('missing.key');
  });
});
