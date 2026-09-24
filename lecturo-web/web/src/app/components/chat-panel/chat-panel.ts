import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import katex from 'katex';
import { splitMath, type Segment } from '../../core/math';
import { ApiError, ApiService, type MessageDto, type SavedPromptDto } from '../../core/api.service';

@Component({
  selector: 'app-chat-panel',
  templateUrl: './chat-panel.html',
  styleUrl: './chat-panel.scss',
})
export class ChatPanel {
  private readonly api = inject(ApiService);

  readonly mode = input<'draft' | 'conversation'>('draft');
  readonly conversationId = input<string | null>(null);
  readonly documentId = input<string | null>(null);
  readonly excerpt = input<string>('');
  readonly imageId = input<string | null>(null);
  readonly provider = input<string>('');
  /** Bumped by the reader when an excerpt was appended out of band, to reload. */
  readonly reloadToken = input<number>(0);

  /** Where the call is routed, for the chat to surface. */
  protected readonly destination = computed(() => {
    const provider = this.provider();
    if (!provider) return '';
    return provider === 'lecturo' ? 'Lecturo relay' : 'Your key';
  });

  protected readonly imageSrc = computed(() => {
    const id = this.imageId();
    return id ? this.api.imageUrl(id) : null;
  });

  readonly close = output<void>();
  readonly started = output<string>();

  protected readonly messages = signal<MessageDto[]>([]);
  protected readonly prompts = signal<SavedPromptDto[]>([]);
  protected readonly composer = signal('');
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly streamingText = signal('');

  private loadedConversation: string | null = null;
  private loadedToken = -1;

  constructor() {
    this.api
      .listPrompts()
      .then((prompts) => this.prompts.set(prompts))
      .catch(() => {});

    effect(() => {
      const id = this.conversationId();
      const token = this.reloadToken();
      if (id && (id !== this.loadedConversation || token !== this.loadedToken))
        void this.loadConversation(id, token);
    });
  }

  private async loadConversation(id: string, token: number): Promise<void> {
    this.loadedConversation = id;
    this.loadedToken = token;
    try {
      const conversation = await this.api.getConversation(id);
      this.messages.set(conversation.messages);
    } catch (error) {
      this.error.set(message(error, 'Could not load this conversation.'));
    }
  }

  protected async send(text?: string, promptId?: string): Promise<void> {
    const content = (text ?? this.composer()).trim();
    if (!content || this.sending()) return;

    this.sending.set(true);
    this.error.set(null);
    this.composer.set('');
    this.streamingText.set('');

    try {
      let conversationId = this.conversationId();
      if (!conversationId) {
        const created = await this.api.createConversation({
          excerpt: this.excerpt() || content,
          documentId: this.documentId(),
          promptId: promptId ?? null,
        });
        conversationId = created.id;
        this.started.emit(conversationId);
      }

      this.messages.update((messages) => [
        ...messages,
        {
          id: `local-${Date.now()}`,
          conversationId,
          role: 'user',
          content,
          superseded: false,
          createdAt: new Date().toISOString(),
        } as MessageDto,
      ]);

      const reply = await this.api.streamMessage(conversationId, content, (delta) => {
        this.streamingText.update((value) => value + delta);
      });
      this.streamingText.set('');
      this.messages.update((messages) => [...messages, reply]);
    } catch (error) {
      this.streamingText.set('');
      this.error.set(message(error, 'The model could not be reached.'));
    } finally {
      this.sending.set(false);
    }
  }

  protected usePrompt(prompt: SavedPromptDto): void {
    void this.send(prompt.body, prompt.id);
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      void this.send();
    }
  }

  protected readonly mathEnabled = signal(readMathEnabled());

  /** Prose and maths, so the maths can be typeset by KaTeX. */
  protected segments(text: string): Segment[] {
    return this.mathEnabled() ? splitMath(text) : [{ kind: 'text', value: text, display: false }];
  }

  protected renderMath(value: string, display: boolean): string {
    try {
      return katex.renderToString(value, { displayMode: display, throwOnError: false });
    } catch {
      return value;
    }
  }

  protected fieldValue(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }
}

function message(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback;
}

function readMathEnabled(): boolean {
  try {
    return localStorage.getItem('lecturo.math') !== 'false';
  } catch {
    return true;
  }
}
