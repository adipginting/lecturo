-- Screenshot crops: a conversation can be seeded by an image instead of an
-- excerpt (the crop becomes the frozen context, sent to vision models).

create table images (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid not null references users(id) on delete cascade,
    content_type text not null,
    byte_size    bigint not null,
    blob_key     text not null,
    created_at   timestamptz not null default now()
);

create index images_user_idx on images (user_id, created_at desc);

alter table conversations
    add column context_image_id uuid references images(id) on delete set null;
