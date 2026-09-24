-- Drafts replace the Saved queue: a draft is a conversation with no `sent_at`,
-- sent once its first reply lands (mirrors the mobile's migration 10 -> 11).
-- A conversation also keeps its excerpt's trace (doc_title) so it survives the
-- document being deleted, and a summary is a Message marked `is_summary` rather
-- than a third role.

alter table conversations add column sent_at timestamptz;
alter table conversations add column doc_title text;

-- Every existing conversation was sent; backfill the trace and the timestamp.
update conversations set sent_at = created_at;
update conversations c
set doc_title = d.title
from documents d
where d.id = c.document_id;

alter table conversations
    alter column title set default 'New conversation';
update conversations set title = 'New conversation' where title is null;
alter table conversations
    alter column title set not null;

alter table messages add column is_summary boolean not null default false;
alter table messages add column image_id uuid references images(id) on delete set null;

update messages set is_summary = true where role = 'summary';
update messages set role = 'assistant' where role in ('summary', 'system');

alter table messages drop constraint messages_role_check;
alter table messages add constraint messages_role_check check (role in ('user', 'assistant'));

-- Every saved excerpt becomes an unsent conversation.
insert into conversations
    (user_id, document_id, title, excerpt, locator, provider, doc_title, sent_at, created_at, updated_at)
select si.user_id, si.document_id, 'New conversation', si.text, si.locator, 'deepseek',
       d.title, null, si.created_at, si.created_at
from saved_items si
left join documents d on d.id = si.document_id;

drop table saved_items;
