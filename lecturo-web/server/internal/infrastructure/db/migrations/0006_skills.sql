-- Skills (F8): a named prompt with a manifest, installed from a marketplace and
-- run against a document. Mirrors the mobile and desktop tables.

create table skills (
    id           uuid primary key default gen_random_uuid(),
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
    id          uuid primary key default gen_random_uuid(),
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
