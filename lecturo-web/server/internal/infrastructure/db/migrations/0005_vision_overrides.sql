-- A per-model override of the vision capability, for a model whose family
-- default is wrong (mirrors the mobile's per-model Settings override).

create table model_vision_overrides (
    user_id         uuid not null references users(id) on delete cascade,
    model           text not null,
    supports_images boolean not null,
    updated_at      timestamptz not null default now(),
    primary key (user_id, model)
);
