import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  ApiError,
  ApiService,
  type ProviderSettingsDto,
  type RemoteSkillDto,
  type SavedPromptDto,
  type ServerSettingsDto,
  type SkillDto,
} from '../../core/api.service';
import { I18nService, type Locale } from '../../core/i18n.service';
import { SessionService } from '../../core/session.service';

type ProviderStatus = ProviderSettingsDto['providers'][number];

@Component({
  selector: 'app-settings',
  templateUrl: './settings.html',
})
export class SettingsScreen {
  private readonly api = inject(ApiService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  protected readonly i18n = inject(I18nService);
  protected readonly locales: Locale[] = ['en', 'id'];

  protected readonly providers = signal<ProviderStatus[]>([]);
  protected readonly providerId = signal('deepseek');
  protected readonly baseUrl = signal('');
  protected readonly model = signal('');
  protected readonly apiKey = signal('');
  protected readonly hasKey = signal(false);
  protected readonly modelSupportsImages = signal(false);
  protected readonly mathEnabled = signal(mathEnabled());
  protected readonly models = signal<string[]>([]);

  protected readonly servers = signal<ServerSettingsDto>({
    calibreHasPassword: false,
    zoteroHasKey: false,
  });
  protected readonly calibreUrl = signal('');
  protected readonly calibreUser = signal('');
  protected readonly calibrePassword = signal('');
  protected readonly zoteroUserId = signal('');
  protected readonly zoteroApiKey = signal('');

  protected readonly prompts = signal<SavedPromptDto[]>([]);
  protected readonly newPromptTitle = signal('');
  protected readonly newPromptBody = signal('');

  protected readonly skills = signal<SkillDto[]>([]);
  protected readonly skillsCatalog = signal<RemoteSkillDto[]>([]);
  protected readonly marketplaceUrl = signal(localStorage.getItem('lecturo.marketplace') ?? '');
  protected readonly skillStatus = signal<string | null>(null);

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  constructor() {
    void this.loadAll();
  }

  protected async loadAll(): Promise<void> {
    this.busy.set(true);
    try {
      const [provider, servers, prompts, skills] = await Promise.all([
        this.api.getProvider(),
        this.api.getServers(),
        this.api.listPrompts(),
        this.api.listSkills(),
      ]);
      this.applyProvider(provider);
      this.applyServers(servers);
      this.prompts.set(prompts);
      this.skills.set(skills);
      this.error.set(null);
    } catch (error) {
      this.error.set(message(error, 'Could not load settings.'));
    } finally {
      this.busy.set(false);
    }
  }

  private applyProvider(settings: ProviderSettingsDto): void {
    this.providers.set(settings.providers);
    this.providerId.set(settings.provider);
    this.baseUrl.set(settings.baseUrl ?? '');
    this.model.set(settings.model ?? '');
    this.hasKey.set(settings.hasKey);
    this.modelSupportsImages.set(settings.modelSupportsImages ?? false);
    this.apiKey.set('');
  }

  private applyServers(settings: ServerSettingsDto): void {
    this.servers.set(settings);
    this.calibreUrl.set(settings.calibreUrl ?? '');
    this.calibreUser.set(settings.calibreUser ?? '');
    this.calibrePassword.set('');
    this.zoteroUserId.set(settings.zoteroUserId ?? '');
    this.zoteroApiKey.set('');
  }

  protected async selectProvider(id: string): Promise<void> {
    try {
      this.applyProvider(await this.api.updateProvider({ provider: id }));
      this.models.set([]);
      this.notice.set(null);
    } catch (error) {
      this.error.set(message(error, 'Could not select that provider.'));
    }
  }

  protected async saveProvider(): Promise<void> {
    this.busy.set(true);
    this.notice.set(null);
    try {
      const patch: Record<string, unknown> = {
        provider: this.providerId(),
        baseUrl: this.baseUrl(),
        model: this.model(),
      };
      if (this.apiKey()) patch['apiKey'] = this.apiKey();
      patch['visionOverride'] = this.modelSupportsImages();
      patch['visionOverride'] = this.modelSupportsImages();
      this.applyProvider(await this.api.updateProvider(patch as never));
      this.notice.set('Provider saved.');
    } catch (error) {
      this.error.set(message(error, 'Could not save the provider.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected async fetchModels(): Promise<void> {
    this.busy.set(true);
    this.notice.set(null);
    try {
      this.models.set(await this.api.listModels());
    } catch (error) {
      this.error.set(message(error, 'Could not fetch the model list.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected chooseModel(name: string): void {
    this.model.set(name);
  }

  protected toggleVision(): void {
    this.modelSupportsImages.update((value: boolean) => !value);
  }

  protected toggleMath(): void {
    this.mathEnabled.update((value: boolean) => {
      try {
        localStorage.setItem('lecturo.math', String(!value));
      } catch {
        /* private mode */
      }
      return !value;
    });
  }

  protected async saveServers(): Promise<void> {
    this.busy.set(true);
    this.notice.set(null);
    try {
      const patch: Record<string, unknown> = {
        calibreUrl: this.calibreUrl(),
        calibreUser: this.calibreUser(),
        zoteroUserId: this.zoteroUserId(),
      };
      if (this.calibrePassword()) patch['calibrePassword'] = this.calibrePassword();
      if (this.zoteroApiKey()) patch['zoteroApiKey'] = this.zoteroApiKey();
      this.applyServers(await this.api.updateServers(patch as never));
      this.notice.set('Servers saved.');
    } catch (error) {
      this.error.set(message(error, 'Could not save server settings.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected async addPrompt(): Promise<void> {
    const title = this.newPromptTitle().trim();
    const body = this.newPromptBody().trim();
    if (!title || !body) return;

    try {
      const prompt = await this.api.createPrompt(title, body);
      this.prompts.update((prompts) => [...prompts, prompt]);
      this.newPromptTitle.set('');
      this.newPromptBody.set('');
    } catch (error) {
      this.error.set(message(error, 'Could not add the prompt.'));
    }
  }

  protected async deletePrompt(prompt: SavedPromptDto): Promise<void> {
    try {
      await this.api.deletePrompt(prompt.id);
      this.prompts.update((prompts) => prompts.filter((p) => p.id !== prompt.id));
    } catch (error) {
      this.error.set(message(error, 'Could not delete the prompt.'));
    }
  }

  protected async browseSkills(): Promise<void> {
    const url = this.marketplaceUrl().trim();
    if (!url) return;
    try {
      localStorage.setItem('lecturo.marketplace', url);
    } catch {
      /* ignore */
    }
    this.skillStatus.set(null);
    try {
      this.skillsCatalog.set(await this.api.browseSkills(url));
      if (this.skillsCatalog().length === 0) this.skillStatus.set('The marketplace is empty.');
    } catch (error) {
      this.skillStatus.set(message(error, 'Could not reach the marketplace.'));
    }
  }

  protected async installSkill(skill: RemoteSkillDto): Promise<void> {
    this.skillStatus.set(null);
    try {
      await this.api.installSkill(skill);
      this.skills.set(await this.api.listSkills());
    } catch (error) {
      this.skillStatus.set(message(error, 'Could not install the skill.'));
    }
  }

  protected async uninstallSkill(skill: SkillDto): Promise<void> {
    try {
      await this.api.uninstallSkill(skill.skillId);
      this.skills.update((skills) => skills.filter((s) => s.id !== skill.id));
    } catch (error) {
      this.skillStatus.set(message(error, 'Could not remove the skill.'));
    }
  }

  protected async toggleSkill(skill: SkillDto): Promise<void> {
    try {
      await this.api.setSkillEnabled(skill.skillId, !skill.enabled);
      this.skills.update((skills) =>
        skills.map((s) => (s.id === skill.id ? { ...s, enabled: !s.enabled } : s)),
      );
    } catch (error) {
      this.skillStatus.set(message(error, 'Could not update the skill.'));
    }
  }

  protected setLocale(locale: Locale): void {
    this.i18n.setLocale(locale);
  }

  protected async signOut(): Promise<void> {
    await this.session.logout();
    await this.router.navigate(['/login']);
  }
}

function message(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback;
}

function mathEnabled(): boolean {
  try {
    return localStorage.getItem('lecturo.math') !== 'false';
  } catch {
    return true;
  }
}
