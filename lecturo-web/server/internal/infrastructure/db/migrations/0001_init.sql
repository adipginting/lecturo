-- Lecturo Web — initial schema.
-- Every row is owned by a user; nothing is shared across accounts.

create table users (
    id            uuid primary key default gen_random_uuid(),
    email         text not null,
    password_hash text not null,
    created_at    timestamptz not null default now()
);

create unique index users_email_lower_idx on users (lower(email));

-- Server sessions. Only a hash of the cookie token is stored.
create table sessions (
    token_hash text primary key,
    user_id    uuid not null references users(id) on delete cascade,
    csrf_token text not null,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null
);

create index sessions_user_idx on sessions (user_id);
create index sessions_expires_idx on sessions (expires_at);

-- The app-owned library. `blob_key` points into the BlobStore; bytes are never
-- stored in Postgres. `locator` is opaque: PDF = page number, EPUB = CFI.
create table documents (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references users(id) on delete cascade,
    title      text not null,
    format     text not null check (format in ('pdf', 'epub')),
    locator    text,
    byte_size  bigint,
    blob_key   text not null,
    source     text check (source in ('calibre', 'zotero')),
    remote_id  text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index documents_user_idx on documents (user_id, updated_at desc);
create unique index documents_remote_uniq
    on documents (user_id, source, remote_id)
    where source is not null and remote_id is not null;

create table saved_items (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references users(id) on delete cascade,
    document_id uuid not null references documents(id) on delete cascade,
    text        text not null,
    locator     text not null,
    created_at  timestamptz not null default now()
);

create index saved_items_user_idx on saved_items (user_id, created_at desc);
create index saved_items_document_idx on saved_items (document_id);

create table saved_prompts (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references users(id) on delete cascade,
    title      text not null,
    body       text not null,
    created_at timestamptz not null default now()
);

create index saved_prompts_user_idx on saved_prompts (user_id, created_at);

-- A conversation is seeded by a frozen excerpt; deleting the document keeps the
-- conversation as history (document_id becomes null).
create table conversations (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references users(id) on delete cascade,
    document_id uuid references documents(id) on delete set null,
    title       text,
    excerpt     text not null,
    locator     text,
    prompt_id   uuid references saved_prompts(id) on delete set null,
    provider    text not null,
    model       text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create index conversations_user_idx on conversations (user_id, updated_at desc);
create index conversations_document_idx
    on conversations (user_id, document_id, updated_at desc);

-- `summary` rows are produced by compaction; `superseded` marks the older
-- messages they replace (kept on screen, excluded from the next request).
create table messages (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null references users(id) on delete cascade,
    conversation_id uuid not null references conversations(id) on delete cascade,
    role            text not null check (role in ('user', 'assistant', 'system', 'summary')),
    content         text not null,
    superseded      boolean not null default false,
    created_at      timestamptz not null default now()
);

create index messages_conversation_idx on messages (conversation_id, created_at);

-- Provider selection per user; the API key is encrypted at rest (bytea).
create table provider_settings (
    user_id     uuid primary key references users(id) on delete cascade,
    provider    text not null default 'deepseek',
    base_url    text,
    model       text,
    api_key_enc bytea,
    updated_at  timestamptz not null default now()
);

-- Calibre/Zotero connection details; secrets encrypted at rest.
create table server_settings (
    user_id               uuid primary key references users(id) on delete cascade,
    calibre_url           text,
    calibre_user          text,
    calibre_password_enc  bytea,
    zotero_user_id        text,
    zotero_api_key_enc    bytea,
    updated_at            timestamptz not null default now()
);
