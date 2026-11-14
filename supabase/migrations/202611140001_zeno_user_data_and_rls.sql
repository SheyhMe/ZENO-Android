-- ZENO user data schema. Every exposed table is protected by per-user RLS.
create table if not exists public.profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  language text not null default 'uz' check (language in ('uz','en')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create table if not exists public.conversations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  title text not null default 'New conversation' check (char_length(title) <= 200),
  is_archived boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (id, user_id)
);
create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null check (role in ('user','assistant','system','tool')),
  content text not null check (char_length(content) between 1 and 100000),
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  constraint messages_conversation_owner_fk foreign key (conversation_id, user_id)
    references public.conversations(id, user_id) on delete cascade
);
create index if not exists conversations_user_updated_idx on public.conversations(user_id, updated_at desc);
create index if not exists messages_conversation_created_idx on public.messages(conversation_id, created_at asc);
create index if not exists messages_user_created_idx on public.messages(user_id, created_at desc);
create table if not exists public.user_memories (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  category text not null default 'general' check (char_length(category) <= 80),
  content text not null check (char_length(content) between 1 and 10000),
  source text not null default 'user',
  importance smallint not null default 3 check (importance between 1 and 5),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists user_memories_owner_active_idx on public.user_memories(user_id, is_active, importance desc, updated_at desc);
create table if not exists public.assistant_preferences (
  user_id uuid primary key references auth.users(id) on delete cascade,
  language text not null default 'uz' check (language in ('uz','en')),
  theme text not null default 'system' check (theme in ('system','light','dark')),
  voice_enabled boolean not null default false,
  wake_phrase_enabled boolean not null default false,
  updated_at timestamptz not null default now(),
  constraint wake_phrase_requires_voice check (not wake_phrase_enabled or voice_enabled)
);
alter table public.profiles enable row level security;
alter table public.conversations enable row level security;
alter table public.messages enable row level security;
alter table public.user_memories enable row level security;
alter table public.assistant_preferences enable row level security;
revoke all on public.profiles, public.conversations, public.messages, public.user_memories, public.assistant_preferences from anon;
grant select, insert, update on public.profiles to authenticated;
grant select, insert, update, delete on public.conversations to authenticated;
grant select, insert, update, delete on public.messages to authenticated;
grant select, insert, update, delete on public.user_memories to authenticated;
grant select, insert, update on public.assistant_preferences to authenticated;
grant usage on schema public to authenticated;
create policy profiles_select_own on public.profiles for select to authenticated using ((select auth.uid()) = user_id);
create policy profiles_insert_own on public.profiles for insert to authenticated with check ((select auth.uid()) = user_id);
create policy profiles_update_own on public.profiles for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy conversations_select_own on public.conversations for select to authenticated using ((select auth.uid()) = user_id);
create policy conversations_insert_own on public.conversations for insert to authenticated with check ((select auth.uid()) = user_id);
create policy conversations_update_own on public.conversations for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy conversations_delete_own on public.conversations for delete to authenticated using ((select auth.uid()) = user_id);
create policy messages_select_own on public.messages for select to authenticated using ((select auth.uid()) = user_id);
create policy messages_insert_own on public.messages for insert to authenticated with check ((select auth.uid()) = user_id);
create policy messages_update_own on public.messages for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy messages_delete_own on public.messages for delete to authenticated using ((select auth.uid()) = user_id);
create policy memories_select_own on public.user_memories for select to authenticated using ((select auth.uid()) = user_id);
create policy memories_insert_own on public.user_memories for insert to authenticated with check ((select auth.uid()) = user_id);
create policy memories_update_own on public.user_memories for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy memories_delete_own on public.user_memories for delete to authenticated using ((select auth.uid()) = user_id);
create policy preferences_select_own on public.assistant_preferences for select to authenticated using ((select auth.uid()) = user_id);
create policy preferences_insert_own on public.assistant_preferences for insert to authenticated with check ((select auth.uid()) = user_id);
create policy preferences_update_own on public.assistant_preferences for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
