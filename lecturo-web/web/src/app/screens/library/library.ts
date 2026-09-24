import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  ApiError,
  ApiService,
  type DocumentDto,
  type RemoteItemDto,
  type RemotePageDto,
} from '../../core/api.service';
import { LayoutService } from '../../core/layout.service';

@Component({
  selector: 'app-library',
  imports: [RouterLink],
  templateUrl: './library.html',
})
export class LibraryScreen {
  protected readonly layout = inject(LayoutService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly tabs = ['Local', 'Calibre', 'Zotero'];
  protected readonly activeTab = signal('Local');

  protected readonly documents = signal<DocumentDto[]>([]);
  protected readonly selected = signal<DocumentDto | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly importUrl = signal('');

  protected readonly remoteSource = computed(() => {
    switch (this.activeTab()) {
      case 'Calibre':
        return 'calibre';
      case 'Zotero':
        return 'zotero';
      default:
        return '';
    }
  });
  protected readonly remotePage = signal<RemotePageDto | null>(null);
  protected readonly remotePath = signal<string | undefined>(undefined);
  protected readonly remoteQuery = signal('');
  protected readonly remoteBusy = signal(false);
  protected readonly remoteError = signal<string | null>(null);

  constructor() {
    void this.refresh();
  }

  protected async refresh(): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      const documents = await this.api.listDocuments();
      this.documents.set(documents);
      if (!this.selected() && documents.length > 0) this.selected.set(documents[0]);
    } catch (error) {
      this.error.set(message(error, 'Could not load your library.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected selectTab(tab: string): void {
    this.activeTab.set(tab);
    this.error.set(null);
    this.remoteError.set(null);
    if (tab !== 'Local' && !this.remotePage()) {
      void this.browse();
    }
  }

  protected async onFiles(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (files.length === 0) return;

    this.busy.set(true);
    this.error.set(null);
    try {
      for (const file of files) {
        const document = await this.api.uploadDocument(file);
        await this.router.navigate(['/reader', document.id]);
      }
      await this.refresh();
    } catch (error) {
      this.error.set(message(error, 'Could not import that file.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected async download(): Promise<void> {
    const url = this.importUrl().trim();
    if (!url || this.busy()) return;

    this.busy.set(true);
    this.error.set(null);
    try {
      const document = await this.api.importDocument(url);
      this.importUrl.set('');
      await this.router.navigate(['/reader', document.id]);
    } catch (error) {
      this.error.set(message(error, 'Could not download that URL.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected open(document: DocumentDto): void {
    void this.router.navigate(['/reader', document.id]);
  }

  protected async remove(document: DocumentDto, event: Event): Promise<void> {
    event.stopPropagation();
    try {
      await this.api.deleteDocument(document.id);
      if (this.selected()?.id === document.id) this.selected.set(null);
      await this.refresh();
    } catch (error) {
      this.error.set(message(error, 'Could not delete that document.'));
    }
  }

  // ---- remote libraries ----
  protected async browse(): Promise<void> {
    const source = this.remoteSource();
    if (!source) return;

    this.remoteBusy.set(true);
    this.remoteError.set(null);
    try {
      this.remotePage.set(await this.api.browseRemote(source, this.remotePath()));
    } catch (error) {
      this.remoteError.set(message(error, 'Could not reach that source.'));
    } finally {
      this.remoteBusy.set(false);
    }
  }

  protected async searchRemote(): Promise<void> {
    const source = this.remoteSource();
    const query = this.remoteQuery().trim();
    if (!source) return;

    this.remoteBusy.set(true);
    this.remoteError.set(null);
    try {
      this.remotePage.set(
        query ? await this.api.searchRemote(source, query) : await this.api.browseRemote(source),
      );
    } catch (error) {
      this.remoteError.set(message(error, 'The search failed.'));
    } finally {
      this.remoteBusy.set(false);
    }
  }

  protected async openRemote(item: RemoteItemDto): Promise<void> {
    if (item.isCatalog) {
      this.remotePath.set(item.url ?? undefined);
      await this.browse();
      return;
    }
    if (item.downloaded && item.documentId) {
      void this.router.navigate(['/reader', item.documentId]);
      return;
    }

    this.remoteBusy.set(true);
    this.remoteError.set(null);
    try {
      const document = await this.api.downloadRemote(this.remoteSource(), item);
      await this.refresh();
      void this.router.navigate(['/reader', document.id]);
    } catch (error) {
      this.remoteError.set(message(error, 'Could not download that item.'));
    } finally {
      this.remoteBusy.set(false);
    }
  }

  protected remoteBack(): void {
    this.remotePath.set(undefined);
    this.remoteQuery.set('');
    void this.browse();
  }

  protected openFilePicker(input: HTMLInputElement): void {
    input.click();
  }

  private static readonly tints = ['#FFD8E4', '#E8DEF8', '#E6E0E9', '#EADDFF'];
  private static readonly fgs = ['#31111D', '#1D192B', '#1C1B1F', '#21005D'];

  protected tint(document: DocumentDto): string {
    return LibraryScreen.tints[hash(document.id) % LibraryScreen.tints.length];
  }

  protected fg(document: DocumentDto): string {
    return LibraryScreen.fgs[hash(document.id) % LibraryScreen.fgs.length];
  }

  protected initial(document: DocumentDto): string {
    return (document.title.trim()[0] ?? '?').toUpperCase();
  }

  protected meta(document: DocumentDto): string {
    const parts = [document.format.toUpperCase()];
    if (document.locator) parts.push(`${document.locator}`);
    if (document.source) parts.push(document.source);
    return parts.join(' · ');
  }

  protected remoteMeta(item: RemoteItemDto): string {
    if (item.isCatalog) return 'Catalog';
    return [item.format?.toUpperCase(), item.author].filter(Boolean).join(' · ');
  }
}

function hash(value: string): number {
  let total = 0;
  for (let i = 0; i < value.length; i++) total = (total * 31 + value.charCodeAt(i)) >>> 0;
  return total;
}

function message(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback;
}
