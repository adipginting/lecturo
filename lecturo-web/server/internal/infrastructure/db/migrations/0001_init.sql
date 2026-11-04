-- Lecturo Web — the schema, as one baseline.
--
-- Pre-release, so the migration history was reset to zero: this single file is
-- the whole schema at its current shape. Every generated primary key is a
-- UUIDv7 (time-ordered, client-mintable), from Postgres 18's built-in
-- `uuidv7()` — no hand-rolled function or pgcrypto needed.
--
-- Every row is owned by a user; nothing is shared across accounts.

create table users (
    id            uuid primary key default uuidv7(),
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
    id         uuid primary key default uuidv7(),
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

create table saved_prompts (
    id         uuid primary key default uuidv7(),
    user_id    uuid not null references users(id) on delete cascade,
    title      text not null,
    body       text not null,
    created_at timestamptz not null default now()
);

create index saved_prompts_user_idx on saved_prompts (user_id, created_at);

-- Screenshot crops: a conversation can be seeded by an image instead of an
-- excerpt (the crop becomes the frozen context, sent to vision models).
create table images (
    id           uuid primary key default uuidv7(),
    user_id      uuid not null references users(id) on delete cascade,
    content_type text not null,
    byte_size    bigint not null,
    blob_key     text not null,
    created_at   timestamptz not null default now()
);

create index images_user_idx on images (user_id, created_at desc);

-- A conversation is seeded by a frozen excerpt; deleting the document keeps the
-- conversation as history (document_id becomes null). A draft is a conversation
-- with no `sent_at`; it is sent once its first reply lands.
create table conversations (
    id               uuid primary key default uuidv7(),
    user_id          uuid not null references users(id) on delete cascade,
    document_id      uuid references documents(id) on delete set null,
    title            text not null default 'New conversation',
    excerpt          text not null,
    locator          text,
    prompt_id        uuid references saved_prompts(id) on delete set null,
    provider         text not null,
    model            text,
    context_image_id uuid references images(id) on delete set null,
    doc_title        text,
    sent_at          timestamptz,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);

create index conversations_user_idx on conversations (user_id, updated_at desc);
create index conversations_document_idx
    on conversations (user_id, document_id, updated_at desc);

-- `is_summary` rows are produced by compaction; `superseded` marks the older
-- messages they replace (kept on screen, excluded from the next request).
create table messages (
    id              uuid primary key default uuidv7(),
    user_id         uuid not null references users(id) on delete cascade,
    conversation_id uuid not null references conversations(id) on delete cascade,
    role            text not null check (role in ('user', 'assistant')),
    content         text not null,
    superseded      boolean not null default false,
    is_summary      boolean not null default false,
    image_id        uuid references images(id) on delete set null,
    created_at      timestamptz not null default now()
);

create index messages_conversation_idx on messages (conversation_id, created_at);

-- Provider selection per user; credentials are per provider, the key encrypted
-- at rest (bytea).
create table provider_settings (
    user_id     uuid primary key references users(id) on delete cascade,
    provider    text not null default 'deepseek',
    updated_at  timestamptz not null default now()
);

create table provider_credentials (
    user_id     uuid not null references users(id) on delete cascade,
    provider    text not null,
    base_url    text,
    model       text,
    api_key_enc bytea,
    updated_at  timestamptz not null default now(),
    primary key (user_id, provider)
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

-- A per-model override of the vision capability, for a model whose family
-- default is wrong.
create table model_vision_overrides (
    user_id         uuid not null references users(id) on delete cascade,
    model           text not null,
    supports_images boolean not null,
    updated_at      timestamptz not null default now(),
    primary key (user_id, model)
);

-- Skills (F8): a named prompt with a manifest, installed from a marketplace and
-- run against a document.
create table skills (
    id           uuid primary key default uuidv7(),
    user_id      uuid not null references users(id) on delete cascade,
    skill_id     text not null,
    name         text not null,
    description  text not null default '',
    author       text not null default '',
    version      text not null default '',
    scope        text not null default 'message',
    trigger      text not null default 'on_demand',
    prompt       text not null default '',
    price        text not null default '',
    enabled      boolean not null default true,
    installed_at timestamptz not null default now(),
    unique (user_id, skill_id)
);

create index skills_user_idx on skills (user_id, name);

create table skill_runs (
    id          uuid primary key default uuidv7(),
    user_id     uuid not null references users(id) on delete cascade,
    skill_id    text not null,
    skill_name  text not null,
    document_id uuid references documents(id) on delete set null,
    status      text not null default 'running',
    result      text not null default '',
    error       text not null default '',
    created_at  timestamptz not null default now()
);

create index skill_runs_user_idx on skill_runs (user_id, created_at desc);
