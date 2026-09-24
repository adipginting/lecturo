import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApiError, ApiService, type ConversationDto } from '../../core/api.service';
import { LayoutService } from '../../core/layout.service';
import { ChatPanel } from '../../components/chat-panel/chat-panel';

type Tab = 'Drafts' | 'Conversations';

/**
 * One surface, two tabs: Drafts (a conversation with no sentAt) and
 * Conversations (sent once their first reply landed).
 */
@Component({
  selector: 'app-chats',
  imports: [ChatPanel],
  templateUrl: './chats.html',
})
export class ChatsScreen {
  protected readonly layout = inject(LayoutService);
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  protected readonly tabs: Tab[] = ['Drafts', 'Conversations'];
  protected readonly tab = signal<Tab>('Conversations');

  protected readonly conversations = signal<ConversationDto[]>([]);
  protected readonly selectedId = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly selected = computed(
    () => this.conversations().find((c) => c.id === this.selectedId()) ?? null,
  );

  constructor() {
    const requested = this.route.snapshot.queryParamMap.get('conversation');
    if (requested) this.selectedId.set(requested);
    void this.load();
  }

  private status(): 'draft' | 'sent' {
    return this.tab() === 'Drafts' ? 'draft' : 'sent';
  }

  protected async load(): Promise<void> {
    this.busy.set(true);
    try {
      const conversations = await this.api.listConversations({ status: this.status() });
      this.conversations.set(conversations);
      if (!this.selectedId() && conversations.length > 0) {
        this.selectedId.set(conversations[0].id);
      }
      this.error.set(null);
    } catch (error) {
      this.error.set(message(error, 'Could not load conversations.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected setTab(tab: Tab): void {
    if (tab === this.tab()) return;
    this.tab.set(tab);
    this.selectedId.set(null);
    void this.load();
  }

  protected select(conversation: ConversationDto): void {
    this.selectedId.set(conversation.id);
  }

  protected async rename(conversation: ConversationDto, event: Event): Promise<void> {
    event.stopPropagation();
    const title = window.prompt('Rename conversation', conversation.title ?? '');
    if (!title || !title.trim()) return;
    try {
      const updated = await this.api.updateConversation(conversation.id, { title: title.trim() });
      this.conversations.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
    } catch (error) {
      this.error.set(message(error, 'Could not rename that conversation.'));
    }
  }

  protected async remove(conversation: ConversationDto, event: Event): Promise<void> {
    event.stopPropagation();
    try {
      await this.api.deleteConversation(conversation.id);
      this.conversations.update((list) => list.filter((c) => c.id !== conversation.id));
      if (this.selectedId() === conversation.id) this.selectedId.set(null);
    } catch (error) {
      this.error.set(message(error, 'Could not delete that conversation.'));
    }
  }

  /** A draft that got its first reply is now a conversation: move it across. */
  protected onSent(): void {
    if (this.tab() === 'Drafts') {
      void this.load();
    }
  }

  protected closePanel(): void {
    this.selectedId.set(null);
  }
}

function message(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback;
}
