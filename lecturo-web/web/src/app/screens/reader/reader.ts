import { Component, ElementRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiError, ApiService, type DocumentDto } from '../../core/api.service';
import { LayoutService } from '../../core/layout.service';
import { ChatPanel } from '../../components/chat-panel/chat-panel';

@Component({
  selector: 'app-reader',
  imports: [ChatPanel, RouterLink],
  templateUrl: './reader.html',
  styleUrl: './reader.scss',
})
export class ReaderScreen {
  protected readonly layout = inject(LayoutService);
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  protected readonly document = signal<DocumentDto | null>(null);
  protected readonly status = signal<string>('');
  protected readonly error = signal<string | null>(null);

  protected readonly chapters = signal<string[]>([]);
  protected readonly chapter = signal('Book II');

  protected readonly pdfPages = signal(0);
  protected readonly pdfPage = signal(1);
  protected readonly saving = signal(false);
  protected readonly savedNotice = signal<string | null>(null);

  protected readonly chatOpen = signal(false);
  protected readonly chatMode = signal<'draft' | 'conversation'>('draft');
  protected readonly conversationId = signal<string | null>(null);
  protected readonly contextImageId = signal<string | null>(null);
  protected readonly excerpt = signal<string>('');
  /** Bumped when an excerpt is appended out of band, to reload the chat panel. */
  protected readonly chatReload = signal(0);

  protected readonly cropping = signal(false);
  protected readonly cropRect = signal<{ x: number; y: number; w: number; h: number } | null>(null);
  protected readonly cropNotice = signal<string | null>(null);
  private cropAnchor: { x: number; y: number } | null = null;

  protected readonly isPdf = computed(() => this.document()?.format === 'pdf');
  protected readonly hasDocument = computed(() => this.document() !== null);

  private readonly pdfHostRef = viewChild<ElementRef<HTMLDivElement>>('pdfHost');
  private readonly epubRef = viewChild<ElementRef<HTMLDivElement>>('epubHost');
  private readonly scrollRef = viewChild<ElementRef<HTMLDivElement>>('readerScroll');

  private pdfDocument: any = null;
  private epubBook: any = null;
  private epubRendition: any = null;

  // Continuous PDF scrolling: every page gets a placeholder, rendered lazily.
  private static readonly pdfScale = 1.4;
  private pdfObserver: IntersectionObserver | null = null;
  private pdfPages_: Array<{
    index: number;
    container: HTMLElement;
    canvas: HTMLCanvasElement;
    text: HTMLElement;
    rendered: boolean;
  }> = [];

  protected readonly sampleParagraphs = [
    'Begin the morning by saying to thyself, I shall meet with the busybody, the ungrateful, arrogant, deceitful, envious, unsocial. All these things happen to them by reason of their ignorance of what is good and evil.',
    'To act against one another then is contrary to nature; and it is acting against one another to be vexed and to turn away.',
    'Whatever this is that I am, it is a little flesh and breath, and the ruling part. Throw away thy books; no longer distract thyself: it is not allowed; but as if thou wast now dying, despise the flesh.',
  ];

  constructor() {
    const chat = this.route.snapshot.queryParamMap.get('chat');
    if (chat === 'draft' || chat === 'conversation') {
      this.chatMode.set(chat);
      this.chatOpen.set(true);
    }

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      void this.load(id);
    } else {
      this.chapters.set(['Book I', 'Book II', 'Book III', 'Book IV']);
    }

    // Re-render once the hosts exist (viewChild resolves after the first change detection).
    effect(() => {
      const pdfHost = this.pdfHostRef();
      const epubHost = this.epubRef();
      if (pdfHost || epubHost) void this.render();
    });
  }

  private pendingFile: ArrayBuffer | null = null;

  private async load(id: string): Promise<void> {
    this.status.set('Opening…');
    this.error.set(null);
    try {
      const document = await this.api.getDocument(id);
      this.document.set(document);
      if (document.locator && document.format === 'pdf') {
        this.pdfPage.set(Number(document.locator) || 1);
      }

      const response = await fetch(this.api.documentFileUrl(id), { credentials: 'include' });
      if (!response.ok) throw new Error(`file ${response.status}`);
      this.pendingFile = await response.arrayBuffer();
      await this.render();
    } catch (error) {
      this.document.set(null);
      this.error.set(error instanceof ApiError ? error.message : 'Could not open that document.');
      this.status.set('');
    }
  }

  private async render(): Promise<void> {
    if (!this.pendingFile || !this.document()) return;
    const file = this.pendingFile;
    this.pendingFile = null;
    this.status.set('');

    if (this.document()!.format === 'pdf') {
      await this.renderPdf(file);
    } else {
      await this.renderEpub(file);
    }
  }

  private async renderPdf(data: ArrayBuffer): Promise<void> {
    try {
      const pdfjs = await import('pdfjs-dist');
      // The worker is copied to the app root by angular.json's asset glob.
      pdfjs.GlobalWorkerOptions.workerSrc = '/pdf.worker.min.mjs';
      this.pdfDocument = await pdfjs.getDocument({ data }).promise;
      this.pdfPages.set(this.pdfDocument.numPages);
      this.chapters.set(
        Array.from({ length: this.pdfDocument.numPages }, (_, i) => `Page ${i + 1}`),
      );
      await this.buildPdfPages();
      this.scrollToPage(this.pdfPage());
    } catch (error) {
      console.error('pdf', error);
      this.error.set('That PDF could not be rendered.');
    }
  }

  /** Lays out every page as a placeholder and renders each as it scrolls in. */
  private async buildPdfPages(): Promise<void> {
    const host = this.pdfHostRef()?.nativeElement;
    if (!host || !this.pdfDocument) return;

    this.pdfObserver?.disconnect();
    this.pdfPages_ = [];
    host.querySelectorAll('.pdf-page').forEach((node) => node.remove());

    this.pdfObserver = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          const index = Number((entry.target as HTMLElement).dataset['page']);
          void this.renderPdfPage(index);
        }
      },
      { root: this.scrollRef()?.nativeElement ?? null, rootMargin: '800px 0px' },
    );

    for (let i = 1; i <= this.pdfDocument.numPages; i++) {
      const page = await this.pdfDocument.getPage(i);
      const viewport = page.getViewport({ scale: ReaderScreen.pdfScale });

      const container = document.createElement('div');
      container.className = 'pdf-page';
      container.dataset['page'] = String(i);
      container.style.width = `${viewport.width}px`;
      container.style.height = `${viewport.height}px`;
      this.applyScaleFactor(container, ReaderScreen.pdfScale);

      const canvas = document.createElement('canvas');
      canvas.className = 'pdf-canvas';
      const text = document.createElement('div');
      text.className = 'textLayer';
      container.append(canvas, text);
      host.appendChild(container);

      this.pdfPages_.push({ index: i, container, canvas, text, rendered: false });
      this.pdfObserver.observe(container);
    }
  }

  private async renderPdfPage(pageNumber: number): Promise<void> {
    const node = this.pdfPages_[pageNumber - 1];
    if (!node || node.rendered || !this.pdfDocument) return;
    node.rendered = true;

    try {
      const page = await this.pdfDocument.getPage(pageNumber);
      const viewport = page.getViewport({ scale: ReaderScreen.pdfScale });
      const ratio = window.devicePixelRatio || 1;
      const context = node.canvas.getContext('2d');
      if (!context) return;

      node.canvas.width = viewport.width * ratio;
      node.canvas.height = viewport.height * ratio;
      node.canvas.style.width = `${viewport.width}px`;
      node.canvas.style.height = `${viewport.height}px`;

      await page.render({
        canvasContext: context,
        viewport,
        transform: ratio === 1 ? undefined : [ratio, 0, 0, ratio, 0, 0],
      }).promise;

      await this.renderPdfTextLayer(page, viewport, node.text);
    } catch (error) {
      node.rendered = false;
      console.error('pdf page', pageNumber, error);
    }
  }

  private async renderPdfTextLayer(page: any, viewport: any, host: HTMLElement): Promise<void> {
    host.replaceChildren();
    host.style.width = `${viewport.width}px`;
    host.style.height = `${viewport.height}px`;
    this.applyScaleFactor(host, viewport.scale);

    try {
      const pdfjs: any = await import('pdfjs-dist');
      const textLayer = new pdfjs.TextLayer({
        textContentSource: page.streamTextContent(),
        container: host,
        viewport,
      });
      await textLayer.render();
    } catch {
      /* selection is a nicety; rendering is not */
    }
  }

  /**
   * pdf.js v6 sizes the text layer's spans with `--total-scale-factor` (and older
   * builds with `--scale-factor`); without it the spans collapse and cannot be
   * selected. Set it on the page and its text layer.
   */
  private applyScaleFactor(element: HTMLElement, scale: number): void {
    element.style.setProperty('--scale-factor', String(scale));
    element.style.setProperty('--total-scale-factor', String(scale));
    element.style.setProperty('--scale-round-x', '1px');
    element.style.setProperty('--scale-round-y', '1px');
  }

  /** Scrolls a page into view and records it as the reader's position. */
  private scrollToPage(pageNumber: number): void {
    const node = this.pdfPages_[pageNumber - 1];
    const scroll = this.scrollRef()?.nativeElement;
    if (!node || !scroll) return;
    scroll.scrollTop = Math.max(0, node.container.offsetTop - 8);
    this.setCurrentPdfPage(pageNumber);
  }

  private setCurrentPdfPage(pageNumber: number): void {
    if (pageNumber === this.pdfPage()) return;
    this.pdfPage.set(pageNumber);
    this.chapter.set(`Page ${pageNumber}`);
    if (this.document()) {
      void this.api.savePosition(this.document()!.id, String(pageNumber)).catch(() => {});
    }
  }

  /** While scrolling, the page nearest the top of the viewport is current. */
  protected onPdfScroll(): void {
    if (!this.isPdf() || this.pdfPages_.length === 0) return;
    const scroll = this.scrollRef()?.nativeElement;
    if (!scroll) return;
    const top = scroll.scrollTop + 60;
    let current = 1;
    for (const node of this.pdfPages_) {
      if (node.container.offsetTop <= top) current = node.index;
      else break;
    }
    this.setCurrentPdfPage(current);
  }

  private async renderEpub(data: ArrayBuffer): Promise<void> {
    const host = this.epubRef()?.nativeElement;
    if (!host) {
      this.pendingFile = data;
      return;
    }

    try {
      const ePub = (await import('epubjs')).default;
      this.epubBook = ePub(data);
      this.epubRendition = this.epubBook.renderTo(host, {
        width: '100%',
        height: '100%',
        spread: 'none',
      });
      await this.epubRendition.display(this.document()?.locator || undefined);

      this.epubBook.loaded.navigation.then((navigation: any) => {
        const labels = (navigation?.toc ?? [])
          .map((item: any) => item.label?.trim())
          .filter(Boolean);
        this.chapters.set(labels.length ? labels : ['Start']);
      });

      this.epubRendition.on('relocated', (location: any) => {
        const label = this.epubBook?.navigation?.get(location?.start?.href)?.label;
        this.chapter.set((label ?? location?.start?.href ?? '').toString());
        void this.api.savePosition(this.document()!.id, location.start.cfi).catch(() => {});
      });

      this.epubRendition.on('selected', (cfiRange: string, contents: any) => {
        const text = contents.window.getSelection()?.toString()?.trim();
        if (text) this.capture(text, cfiRange);
      });
    } catch (error) {
      console.error('epub', error);
      this.error.set('That EPUB could not be rendered.');
    }
  }

  protected pdfNext(): void {
    if (this.pdfPage() < this.pdfPages()) this.scrollToPage(this.pdfPage() + 1);
  }

  protected pdfPrev(): void {
    if (this.pdfPage() > 1) this.scrollToPage(this.pdfPage() - 1);
  }

  protected epubStep(delta: number): void {
    this.epubRendition?.next?.();
    if (delta < 0) this.epubRendition?.prev?.();
  }

  protected step(delta: number): void {
    if (this.isPdf()) {
      delta > 0 ? this.pdfNext() : this.pdfPrev();
    } else {
      delta > 0 ? this.epubRendition?.next?.() : this.epubRendition?.prev?.();
    }
  }

  protected capture(text: string, locator: string): void {
    this.selectionText.set(text);
    this.selectionLocator.set(locator);
  }

  protected readonly selectionText = signal<string | null>(null);
  private readonly selectionLocator = signal<string>('');

  protected onSelectionEnd(): void {
    const selected = window.getSelection()?.toString()?.trim();
    if (selected && selected.length > 0) {
      const locator = this.isPdf() ? String(this.pdfPage()) : this.chapter();
      this.capture(selected, locator);
    }
  }

  // ---- screenshot crop ----
  protected startCrop(): void {
    this.cropNotice.set(null);
    if (!this.isPdf()) {
      this.cropNotice.set('Screenshots are available for PDFs for now.');
      return;
    }
    this.cropRect.set(null);
    this.cropping.set(true);
  }

  protected cancelCrop(): void {
    this.cropping.set(false);
    this.cropRect.set(null);
    this.cropAnchor = null;
  }

  protected onCropDown(event: PointerEvent): void {
    const host = event.currentTarget as HTMLElement;
    host.setPointerCapture(event.pointerId);
    const bounds = host.getBoundingClientRect();
    this.cropAnchor = { x: event.clientX - bounds.left, y: event.clientY - bounds.top };
    this.cropRect.set({ ...this.cropAnchor, w: 0, h: 0 });
  }

  protected onCropMove(event: PointerEvent): void {
    const anchor = this.cropAnchor;
    if (!anchor) return;
    const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const x = event.clientX - bounds.left;
    const y = event.clientY - bounds.top;
    this.cropRect.set({
      x: Math.min(anchor.x, x),
      y: Math.min(anchor.y, y),
      w: Math.abs(x - anchor.x),
      h: Math.abs(y - anchor.y),
    });
  }

  protected onCropUp(): void {
    this.cropAnchor = null;
  }

  protected async confirmCrop(): Promise<void> {
    const rect = this.cropRect();
    const host = this.pdfHostRef()?.nativeElement;
    this.cropping.set(false);

    if (!rect || !host || rect.w < 8 || rect.h < 8) {
      this.cropRect.set(null);
      return;
    }

    this.saving.set(true);
    this.cropNotice.set(null);
    try {
      const ratio = window.devicePixelRatio || 1;
      const hostBounds = host.getBoundingClientRect();
      const offscreen = document.createElement('canvas');
      offscreen.width = Math.max(1, Math.round(rect.w * ratio));
      offscreen.height = Math.max(1, Math.round(rect.h * ratio));
      const context = offscreen.getContext('2d');
      if (!context) throw new Error('no 2d context');

      // Composite the pages under the rectangle into one crop.
      for (const node of this.pdfPages_) {
        const bounds = node.canvas.getBoundingClientRect();
        if (bounds.width === 0) continue;

        const pageX = bounds.left - hostBounds.left;
        const pageY = bounds.top - hostBounds.top;
        const left = Math.max(pageX, rect.x);
        const top = Math.max(pageY, rect.y);
        const right = Math.min(pageX + bounds.width, rect.x + rect.w);
        const bottom = Math.min(pageY + bounds.height, rect.y + rect.h);
        if (right <= left || bottom <= top) continue;

        const scale = node.canvas.width / bounds.width;
        context.drawImage(
          node.canvas,
          (left - pageX) * scale,
          (top - pageY) * scale,
          (right - left) * scale,
          (bottom - top) * scale,
          (left - rect.x) * ratio,
          (top - rect.y) * ratio,
          (right - left) * ratio,
          (bottom - top) * ratio,
        );
      }

      const blob = await new Promise<Blob | null>((resolve) =>
        offscreen.toBlob(resolve, 'image/png'),
      );
      if (!blob) throw new Error('no image');

      const image = await this.api.uploadImage(blob);

      // A conversation already open for this document takes the screenshot
      // instead of starting another (ADR 0003).
      const open = this.chatMode() === 'conversation' ? this.conversationId() : null;
      if (open) {
        await this.api.sendMessage(open, '', image.id);
        this.contextImageId.set(image.id);
        this.chatOpen.set(true);
        this.chatReload.update((n) => n + 1);
        return;
      }

      const conversation = await this.api.createConversation({
        excerpt: '',
        documentId: this.document()?.id ?? null,
        imageId: image.id,
      });
      this.conversationId.set(conversation.id);
      this.contextImageId.set(image.id);
      this.excerpt.set('');
      this.chatMode.set('draft');
      this.chatOpen.set(true);
    } catch {
      this.cropNotice.set('Could not capture that region.');
    } finally {
      this.saving.set(false);
      this.cropRect.set(null);
    }
  }

  protected async askAi(): Promise<void> {
    const text = this.selectionText() ?? this.currentExcerpt();
    if (!text) return;

    // While a conversation is open for this document, the excerpt joins it rather
    // than starting another (ADR 0003). With nothing open, it makes a draft.
    const open = this.chatMode() === 'conversation' ? this.conversationId() : null;

    this.saving.set(true);
    try {
      if (open) {
        await this.api.sendMessage(open, text);
        this.excerpt.set(text);
        this.chatOpen.set(true);
        this.chatReload.update((n) => n + 1);
        return;
      }

      const conversation = await this.api.createConversation({
        excerpt: text,
        documentId: this.document()?.id ?? null,
        locator: this.selectionLocator() || null,
      });
      this.conversationId.set(conversation.id);
      this.contextImageId.set(null);
      this.excerpt.set(text);
      this.chatMode.set('draft');
      this.chatOpen.set(true);
    } catch {
      this.savedNotice.set('Could not start a chat.');
    } finally {
      this.saving.set(false);
    }
  }

  private currentExcerpt(): string {
    return this.selectionText() ?? '';
  }

  protected openChat(mode: 'draft' | 'conversation'): void {
    this.excerpt.set(this.selectionText() ?? '');
    this.chatMode.set(mode);
    this.chatOpen.set(true);
  }

  protected dismissSelection(): void {
    this.selectionText.set(null);
    window.getSelection()?.removeAllRanges();
  }
}
