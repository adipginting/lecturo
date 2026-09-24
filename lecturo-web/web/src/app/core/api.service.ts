import { Injectable } from '@angular/core';
import type { components } from './api-schema';

type Schemas = components['schemas'];

export type UserDto = Schemas['User'];
export type DocumentDto = Schemas['Document'];
export type SavedPromptDto = Schemas['SavedPrompt'];
export type ConversationDto = Schemas['Conversation'];
export type MessageDto = Schemas['Message'];
export type ProviderSettingsDto = Schemas['ProviderSettings'];
export type ServerSettingsDto = Schemas['ServerSettings'];
export type RemotePageDto = Schemas['RemotePage'];
export type RemoteItemDto = Schemas['RemoteItem'];
export type ImageDto = Schemas['Image'];

/** An API failure carrying the server's stable error code. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export interface RemoteSource {
  readonly id: 'calibre' | 'zotero';
  readonly label: string;
}

const BASE = '/api/v1';

@Injectable({ providedIn: 'root' })
export class ApiService {
  // ---- auth ----
  register(email: string, password: string): Promise<UserDto> {
    return this.request('POST', '/auth/register', { email, password });
  }

  login(email: string, password: string): Promise<UserDto> {
    return this.request('POST', '/auth/login', { email, password });
  }

  async logout(): Promise<void> {
    await this.request<void>('POST', '/auth/logout');
  }

  me(): Promise<UserDto> {
    return this.request('GET', '/auth/me');
  }

  // ---- documents ----
  async listDocuments(): Promise<DocumentDto[]> {
    const page = await this.request<{ items: DocumentDto[] }>('GET', '/documents');
    return page.items;
  }

  getDocument(id: string): Promise<DocumentDto> {
    return this.request('GET', `/documents/${id}`);
  }

  async uploadDocument(file: File): Promise<DocumentDto> {
    const form = new FormData();
    form.append('file', file);
    return this.send<DocumentDto>('POST', '/documents', form, true);
  }

  importDocument(url: string): Promise<DocumentDto> {
    return this.request('POST', '/documents/import-url', { url });
  }

  deleteDocument(id: string): Promise<void> {
    return this.request('DELETE', `/documents/${id}`);
  }

  savePosition(id: string, locator: string): Promise<void> {
    return this.request('PUT', `/documents/${id}/position`, { locator });
  }

  /** Streaming URL for the in-browser reader (cookies accompany same-origin requests). */
  documentFileUrl(id: string): string {
    return `${BASE}/documents/${id}/file`;
  }

  // ---- images (screenshot crops) ----
  uploadImage(blob: Blob): Promise<ImageDto> {
    const form = new FormData();
    form.append('file', blob, 'crop.png');
    return this.send<ImageDto>('POST', '/images', form, true);
  }

  /** Same-origin URL for an image (cookies accompany the request). */
  imageUrl(id: string): string {
    return `${BASE}/images/${id}`;
  }

  // ---- prompts ----
  async listPrompts(): Promise<SavedPromptDto[]> {
    const page = await this.request<{ items: SavedPromptDto[] }>('GET', '/prompts');
    return page.items;
  }

  createPrompt(title: string, body: string): Promise<SavedPromptDto> {
    return this.request('POST', '/prompts', { title, body });
  }

  updatePrompt(id: string, patch: { title?: string; body?: string }): Promise<SavedPromptDto> {
    return this.request('PUT', `/prompts/${id}`, patch);
  }

  deletePrompt(id: string): Promise<void> {
    return this.request('DELETE', `/prompts/${id}`);
  }

  // ---- skills ----
  async listSkills(): Promise<SkillDto[]> {
    const page = await this.request<{ items: SkillDto[] }>('GET', '/skills');
    return page.items;
  }

  async browseSkills(url: string): Promise<RemoteSkillDto[]> {
    const page = await this.request<{ items: RemoteSkillDto[] }>('POST', '/skills/browse', { url });
    return page.items;
  }

  installSkill(skill: RemoteSkillDto): Promise<void> {
    return this.request('POST', '/skills', skill);
  }

  uninstallSkill(skillId: string): Promise<void> {
    return this.request('DELETE', `/skills/${encodeURIComponent(skillId)}`);
  }

  setSkillEnabled(skillId: string, enabled: boolean): Promise<void> {
    return this.request('PATCH', `/skills/${encodeURIComponent(skillId)}`, { enabled });
  }

  async listSkillRuns(documentId?: string): Promise<SkillRunDto[]> {
    const query = documentId ? `?documentId=${encodeURIComponent(documentId)}` : '';
    const page = await this.request<{ items: SkillRunDto[] }>('GET', `/skills/runs${query}`);
    return page.items;
  }

  // ---- provider ----
  getProvider(): Promise<ProviderSettingsDto> {
    return this.request('GET', '/provider');
  }

  updateProvider(patch: {
    provider: string;
    baseUrl?: string | null;
    model?: string | null;
    apiKey?: string | null;
    visionOverride?: boolean | null;
  }): Promise<ProviderSettingsDto> {
    return this.request('PUT', '/provider', patch);
  }

  async listModels(): Promise<string[]> {
    const page = await this.request<{ items: string[] }>('GET', '/provider/models');
    return page.items;
  }

  // ---- conversations ----
  async listConversations(
    options: { documentId?: string; status?: 'draft' | 'sent' } = {},
  ): Promise<ConversationDto[]> {
    const params = new URLSearchParams();
    if (options.documentId) params.set('documentId', options.documentId);
    if (options.status) params.set('status', options.status);
    const query = params.toString();
    const page = await this.request<{ items: ConversationDto[] }>(
      'GET',
      `/conversations${query ? `?${query}` : ''}`,
    );
    return page.items;
  }

  createConversation(input: {
    excerpt?: string;
    documentId?: string | null;
    locator?: string | null;
    promptId?: string | null;
    imageId?: string | null;
    provider?: string;
    model?: string | null;
  }): Promise<ConversationDto> {
    return this.request('POST', '/conversations', { excerpt: input.excerpt ?? '', ...input });
  }

  getConversation(id: string): Promise<ConversationDto & { messages: MessageDto[] }> {
    return this.request('GET', `/conversations/${id}`);
  }

  updateConversation(
    id: string,
    patch: { title?: string; provider?: string; model?: string | null },
  ): Promise<ConversationDto> {
    return this.request('PATCH', `/conversations/${id}`, patch);
  }

  deleteConversation(id: string): Promise<void> {
    return this.request('DELETE', `/conversations/${id}`);
  }

  sendMessage(conversationId: string, content: string, imageId?: string): Promise<MessageDto> {
    return this.request('POST', `/conversations/${conversationId}/messages?stream=false`, {
      content,
      ...(imageId ? { imageId } : {}),
    });
  }

  compactConversation(conversationId: string): Promise<MessageDto> {
    return this.request('POST', `/conversations/${conversationId}/compact`);
  }

  /**
   * Streams a reply. Calls onDelta for each chunk and resolves with the final
   * message. Uses fetch (not EventSource) so the CSRF header can be sent.
   */
  async streamMessage(
    conversationId: string,
    content: string,
    onDelta: (text: string) => void,
  ): Promise<MessageDto> {
    const response = await fetch(`${BASE}/conversations/${conversationId}/messages`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-CSRF-Token': this.csrfToken(),
      },
      body: JSON.stringify({ content }),
    });
    if (!response.ok || !response.body) {
      throw await this.toError(response);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let done: MessageDto | null = null;
    let failure: ApiError | null = null;

    for (;;) {
      const { value, done: streamDone } = await reader.read();
      if (streamDone) break;
      buffer += decoder.decode(value, { stream: true });

      let boundary = buffer.indexOf('\n\n');
      while (boundary >= 0) {
        const raw = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        const parsed = parseEvent(raw);
        if (parsed?.event === 'delta') onDelta(parsed.data.text ?? '');
        if (parsed?.event === 'done') done = parsed.data.message as MessageDto;
        if (parsed?.event === 'error') {
          failure = new ApiError(
            502,
            parsed.data.error?.code ?? 'upstream_failed',
            parsed.data.error?.message ?? 'The model could not be reached.',
          );
        }
        boundary = buffer.indexOf('\n\n');
      }
    }

    if (failure) throw failure;
    if (!done) throw new ApiError(502, 'upstream_failed', 'The reply ended unexpectedly.');
    return done;
  }

  // ---- servers ----
  getServers(): Promise<ServerSettingsDto> {
    return this.request('GET', '/servers');
  }

  updateServers(patch: {
    calibreUrl?: string | null;
    calibreUser?: string | null;
    calibrePassword?: string | null;
    zoteroUserId?: string | null;
    zoteroApiKey?: string | null;
  }): Promise<ServerSettingsDto> {
    return this.request('PUT', '/servers', patch);
  }

  // ---- remote libraries ----
  browseRemote(source: string, path?: string): Promise<RemotePageDto> {
    const query = path ? `?path=${encodeURIComponent(path)}` : '';
    return this.request('GET', `/remote/${source}/browse${query}`);
  }

  searchRemote(source: string, q: string): Promise<RemotePageDto> {
    return this.request('GET', `/remote/${source}/search?q=${encodeURIComponent(q)}`);
  }

  downloadRemote(source: string, item: RemoteItemDto): Promise<DocumentDto> {
    return this.request('POST', `/remote/${source}/download`, {
      remoteId: item.remoteId,
      url: item.url ?? '',
      title: item.title,
    });
  }

  // ---- plumbing ----
  private request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const isForm = body instanceof FormData;
    return this.send<T>(method, path, body, isForm);
  }

  private async send<T>(method: string, path: string, body: unknown, isForm: boolean): Promise<T> {
    const headers: Record<string, string> = {};
    if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json';
    if (method !== 'GET' && method !== 'HEAD') headers['X-CSRF-Token'] = this.csrfToken();

    const response = await fetch(`${BASE}${path}`, {
      method,
      credentials: 'include',
      headers,
      body: body === undefined ? undefined : isForm ? (body as FormData) : JSON.stringify(body),
    });

    if (!response.ok) throw await this.toError(response);
    if (response.status === 204) return undefined as T;

    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }

  private async toError(response: Response): Promise<ApiError> {
    let code = 'internal_error';
    let message = `Request failed (${response.status}).`;
    try {
      const payload = await response.json();
      if (payload?.error?.code) code = payload.error.code;
      if (payload?.error?.message) message = payload.error.message;
    } catch {
      /* keep the defaults */
    }
    return new ApiError(response.status, code, message);
  }

  private csrfToken(): string {
    const match = document.cookie.match(/(?:^|;\s*)lecturo_csrf=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : '';
  }
}

interface ParsedEvent {
  event: string;
  data: any;
}

function parseEvent(raw: string): ParsedEvent | null {
  let event = 'message';
  const dataLines: string[] = [];
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim();
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
  }
  if (dataLines.length === 0) return null;
  try {
    return { event, data: JSON.parse(dataLines.join('\n')) };
  } catch {
    return null;
  }
}

export interface SkillDto {
  id: string;
  skillId: string;
  name: string;
  description: string;
  author: string;
  version: string;
  scope: string;
  trigger: string;
  prompt: string;
  price: string;
  enabled: boolean;
}

export interface RemoteSkillDto {
  id: string;
  name: string;
  description: string;
  author: string;
  version: string;
  scope: string;
  trigger: string;
  prompt: string;
  price: string;
  paid: boolean;
}

export interface SkillRunDto {
  id: string;
  skillId: string;
  skillName: string;
  documentId: string | null;
  status: string;
  result: string;
  error: string;
  createdAt: string;
}
