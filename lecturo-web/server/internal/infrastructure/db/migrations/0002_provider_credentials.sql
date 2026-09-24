-- Provider credentials become per-provider (one base URL / model / key each),
-- while `provider_settings` keeps just the selected provider.

create table provider_credentials (
    user_id     uuid not null references users(id) on delete cascade,
    provider    text not null,
    base_url    text,
    model       text,
    api_key_enc bytea,
    updated_at  timestamptz not null default now(),
    primary key (user_id, provider)
);

insert into provider_credentials (user_id, provider, base_url, model, api_key_enc, updated_at)
select user_id, provider, base_url, model, api_key_enc, updated_at
from provider_settings
on conflict do nothing;

alter table provider_settings drop column base_url;
alter table provider_settings drop column model;
alter table provider_settings drop column api_key_enc;
