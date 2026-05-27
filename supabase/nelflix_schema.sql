create extension if not exists pgcrypto;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create table if not exists public.profiles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  profile_index integer not null check (profile_index between 1 and 4),
  profile_id integer generated always as (profile_index) stored,
  name text not null default '',
  avatar_color_hex text not null default '#1E88E5',
  uses_primary_addons boolean not null default false,
  uses_primary_plugins boolean not null default false,
  avatar_id text,
  avatar_url text,
  pin_enabled boolean not null default false,
  pin_hash text,
  pin_updated_at timestamptz,
  failed_pin_attempts integer not null default 0,
  pin_locked_until timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_index)
);

create table if not exists public.addons (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  profile_id integer not null check (profile_id between 1 and 4),
  url text not null,
  name text not null default '',
  enabled boolean not null default true,
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_id, url)
);

create table if not exists public.plugins (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  profile_id integer not null check (profile_id between 1 and 4),
  url text not null,
  name text not null default '',
  enabled boolean not null default true,
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_id, url)
);

create table if not exists public.watched_items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  profile_id integer not null check (profile_id between 1 and 4),
  content_id text not null,
  content_type text not null default '',
  title text not null default '',
  season integer,
  episode integer,
  watched_at bigint not null default 0,
  created_at timestamptz not null default now()
);

create unique index if not exists watched_items_user_profile_key
  on public.watched_items (user_id, profile_id, content_id, coalesce(season, -1), coalesce(episode, -1));

create table if not exists public.watch_progress (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  profile_id integer not null check (profile_id between 1 and 4),
  content_id text not null,
  content_type text not null default '',
  video_id text not null default '',
  season integer,
  episode integer,
  position bigint not null default 0,
  duration bigint not null default 0,
  last_watched bigint not null default 0,
  progress_key text not null,
  unique (user_id, profile_id, progress_key)
);

create table if not exists public.library_items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  profile_id integer not null check (profile_id between 1 and 4),
  content_id text not null,
  content_type text not null default '',
  name text not null default '',
  poster text,
  poster_shape text not null default 'POSTER',
  background text,
  description text,
  release_info text,
  imdb_rating numeric,
  genres text[] not null default '{}',
  addon_base_url text,
  added_at bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_id, content_id)
);

create table if not exists public.collections (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  profile_id integer not null check (profile_id between 1 and 4),
  collections_json jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_id)
);

create table if not exists public.profile_settings_blobs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  profile_id integer not null check (profile_id between 1 and 4),
  platform text not null default 'mobile',
  settings_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_id, platform)
);

create table if not exists public.home_catalog_settings (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  profile_id integer not null check (profile_id between 1 and 4),
  platform text not null default 'mobile',
  settings_json jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now(),
  unique (user_id, profile_id, platform)
);

create table if not exists public.avatar_catalog (
  id text primary key,
  display_name text not null default '',
  storage_path text not null default '',
  category text not null default 'character',
  sort_order integer not null default 0,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  bg_color text
);

create table if not exists public.watch_together_rooms (
  id uuid primary key default gen_random_uuid(),
  room_code text unique not null,
  host_user_id uuid not null references auth.users(id) on delete cascade,
  host_profile_id integer not null default 1,
  title text not null default '',
  content_metadata jsonb not null default '{}'::jsonb,
  source_url text not null default '',
  source_headers jsonb not null default '{}'::jsonb,
  stream_title text not null default '',
  provider_name text not null default '',
  playback_state text not null default 'paused',
  position_ms bigint not null default 0,
  duration_ms bigint not null default 0,
  playback_speed real not null default 1,
  closed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint watch_together_playback_state_check check (playback_state in ('playing', 'paused', 'loading', 'ended'))
);

alter table public.watch_together_rooms
  add column if not exists content_metadata jsonb not null default '{}'::jsonb;

create table if not exists public.watch_together_members (
  room_id uuid not null references public.watch_together_rooms(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id integer not null default 1,
  display_name text not null default '',
  joined_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  primary key (room_id, user_id)
);

do $$
declare
  table_name text;
begin
  foreach table_name in array array[
    'profiles',
    'addons',
    'plugins',
    'library_items',
    'collections',
    'profile_settings_blobs'
  ] loop
    execute format('drop trigger if exists %I_set_updated_at on public.%I', table_name, table_name);
    execute format('create trigger %I_set_updated_at before update on public.%I for each row execute function public.set_updated_at()', table_name, table_name);
  end loop;
end;
$$;

alter table public.profiles enable row level security;
alter table public.addons enable row level security;
alter table public.plugins enable row level security;
alter table public.watched_items enable row level security;
alter table public.watch_progress enable row level security;
alter table public.library_items enable row level security;
alter table public.collections enable row level security;
alter table public.profile_settings_blobs enable row level security;
alter table public.home_catalog_settings enable row level security;
alter table public.avatar_catalog enable row level security;
alter table public.watch_together_rooms enable row level security;
alter table public.watch_together_members enable row level security;

do $$
declare
  table_name text;
begin
  foreach table_name in array array[
    'profiles',
    'addons',
    'plugins',
    'watched_items',
    'watch_progress',
    'library_items',
    'collections',
    'profile_settings_blobs',
    'home_catalog_settings'
  ] loop
    execute format('drop policy if exists %I_owner_all on public.%I', table_name, table_name);
    execute format(
      'create policy %I_owner_all on public.%I for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid())',
      table_name,
      table_name
    );
  end loop;
end;
$$;

drop policy if exists avatar_catalog_public_read on public.avatar_catalog;
create policy avatar_catalog_public_read on public.avatar_catalog
  for select to anon, authenticated using (is_active = true);

drop policy if exists watch_together_rooms_member_read on public.watch_together_rooms;
create policy watch_together_rooms_member_read on public.watch_together_rooms
  for select to authenticated using (
    host_user_id = auth.uid()
    or exists (
      select 1
      from public.watch_together_members m
      where m.room_id = id and m.user_id = auth.uid()
    )
  );

drop policy if exists watch_together_rooms_host_write on public.watch_together_rooms;
create policy watch_together_rooms_host_write on public.watch_together_rooms
  for update to authenticated using (host_user_id = auth.uid()) with check (host_user_id = auth.uid());

drop policy if exists watch_together_members_self_read on public.watch_together_members;
create policy watch_together_members_self_read on public.watch_together_members
  for select to authenticated using (
    user_id = auth.uid()
    or exists (
      select 1
      from public.watch_together_rooms r
      where r.id = room_id and r.host_user_id = auth.uid()
    )
  );

drop policy if exists watch_together_members_self_write on public.watch_together_members;
create policy watch_together_members_self_write on public.watch_together_members
  for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

insert into storage.buckets (id, name, public)
values ('avatars', 'avatars', true)
on conflict (id) do update set public = excluded.public;

drop policy if exists avatars_public_read on storage.objects;
create policy avatars_public_read on storage.objects
  for select to anon, authenticated using (bucket_id = 'avatars');

create or replace function public.ensure_default_profile()
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  insert into public.profiles (user_id, profile_index, name, avatar_color_hex)
  values (auth.uid(), 1, 'Profile 1', '#1E88E5')
  on conflict (user_id, profile_index) do nothing;
end;
$$;

create or replace function public.sync_pull_profiles()
returns table (
  id uuid,
  user_id uuid,
  profile_index integer,
  name text,
  avatar_color_hex text,
  avatar_id text,
  avatar_url text,
  uses_primary_addons boolean,
  uses_primary_plugins boolean,
  pin_enabled boolean,
  pin_locked_until timestamptz,
  created_at timestamptz,
  updated_at timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
begin
  perform public.ensure_default_profile();

  return query
  select p.id, p.user_id, p.profile_index, p.name, p.avatar_color_hex, p.avatar_id, p.avatar_url,
    p.uses_primary_addons, p.uses_primary_plugins, p.pin_enabled, p.pin_locked_until, p.created_at, p.updated_at
  from public.profiles p
  where p.user_id = auth.uid()
  order by p.profile_index;
end;
$$;

create or replace function public.sync_push_profiles(p_profiles jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  item jsonb;
  keep_indexes integer[] := '{}';
  idx integer;
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  for item in select * from jsonb_array_elements(coalesce(p_profiles, '[]'::jsonb)) loop
    idx := (item->>'profile_index')::integer;
    if idx between 1 and 4 then
      keep_indexes := array_append(keep_indexes, idx);
      insert into public.profiles (
        user_id, profile_index, name, avatar_color_hex, uses_primary_addons,
        uses_primary_plugins, avatar_id, avatar_url
      )
      values (
        auth.uid(),
        idx,
        coalesce(item->>'name', ''),
        coalesce(item->>'avatar_color_hex', '#1E88E5'),
        coalesce((item->>'uses_primary_addons')::boolean, false),
        coalesce((item->>'uses_primary_plugins')::boolean, false),
        nullif(item->>'avatar_id', ''),
        nullif(item->>'avatar_url', '')
      )
      on conflict (user_id, profile_index) do update set
        name = excluded.name,
        avatar_color_hex = excluded.avatar_color_hex,
        uses_primary_addons = excluded.uses_primary_addons,
        uses_primary_plugins = excluded.uses_primary_plugins,
        avatar_id = excluded.avatar_id,
        avatar_url = excluded.avatar_url,
        updated_at = now();
    end if;
  end loop;

  if array_length(keep_indexes, 1) is not null then
    delete from public.profiles
    where user_id = auth.uid()
      and profile_index <> all(keep_indexes);
  end if;
end;
$$;

create or replace function public.sync_delete_profile_data(p_profile_id integer)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  delete from public.addons where user_id = auth.uid() and profile_id = p_profile_id;
  delete from public.plugins where user_id = auth.uid() and profile_id = p_profile_id;
  delete from public.watched_items where user_id = auth.uid() and profile_id = p_profile_id;
  delete from public.watch_progress where user_id = auth.uid() and profile_id = p_profile_id;
  delete from public.library_items where user_id = auth.uid() and profile_id = p_profile_id;
  delete from public.collections where user_id = auth.uid() and profile_id = p_profile_id;
  delete from public.profile_settings_blobs where user_id = auth.uid() and profile_id = p_profile_id;
  delete from public.home_catalog_settings where user_id = auth.uid() and profile_id = p_profile_id;
  delete from public.profiles where user_id = auth.uid() and profile_index = p_profile_id;
end;
$$;

create or replace function public.sync_pull_profile_locks()
returns table (profile_index integer, pin_enabled boolean, pin_locked_until timestamptz)
language plpgsql
security definer
set search_path = public
as $$
begin
  perform public.ensure_default_profile();
  return query
  select p.profile_index, p.pin_enabled, p.pin_locked_until
  from public.profiles p
  where p.user_id = auth.uid()
  order by p.profile_index;
end;
$$;

create or replace function public.verify_profile_pin(p_profile_id integer, p_pin text)
returns table (unlocked boolean, retry_after_seconds integer, message text)
language plpgsql
security definer
set search_path = public
as $$
declare
  p public.profiles%rowtype;
  retry integer;
begin
  select * into p
  from public.profiles
  where user_id = auth.uid() and profile_index = p_profile_id;

  if not found or not p.pin_enabled then
    return query select true, 0, null::text;
    return;
  end if;

  if p.pin_locked_until is not null and p.pin_locked_until > now() then
    retry := greatest(0, extract(epoch from (p.pin_locked_until - now()))::integer);
    return query select false, retry, 'Too many failed attempts. Try again later.'::text;
    return;
  end if;

  if p.pin_hash is not null and crypt(coalesce(p_pin, ''), p.pin_hash) = p.pin_hash then
    update public.profiles
    set failed_pin_attempts = 0, pin_locked_until = null
    where user_id = auth.uid() and profile_index = p_profile_id;
    return query select true, 0, null::text;
    return;
  end if;

  update public.profiles
  set failed_pin_attempts = failed_pin_attempts + 1,
      pin_locked_until = case when failed_pin_attempts + 1 >= 5 then now() + interval '5 minutes' else null end
  where user_id = auth.uid() and profile_index = p_profile_id;

  return query select false, 0, 'Incorrect PIN.'::text;
end;
$$;

create or replace function public.set_profile_pin(p_profile_id integer, p_pin text, p_current_pin text default null)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  p public.profiles%rowtype;
begin
  select * into p
  from public.profiles
  where user_id = auth.uid() and profile_index = p_profile_id;

  if not found then
    raise exception 'Profile not found';
  end if;

  if p.pin_enabled and (p_current_pin is null or crypt(p_current_pin, p.pin_hash) <> p.pin_hash) then
    raise exception 'Current PIN is incorrect';
  end if;

  update public.profiles
  set pin_enabled = true,
      pin_hash = crypt(coalesce(p_pin, ''), gen_salt('bf')),
      pin_updated_at = now(),
      failed_pin_attempts = 0,
      pin_locked_until = null
  where user_id = auth.uid() and profile_index = p_profile_id;
end;
$$;

create or replace function public.clear_profile_pin(p_profile_id integer, p_current_pin text default null)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  p public.profiles%rowtype;
begin
  select * into p
  from public.profiles
  where user_id = auth.uid() and profile_index = p_profile_id;

  if not found then
    raise exception 'Profile not found';
  end if;

  if p.pin_enabled and p_current_pin is not null and crypt(p_current_pin, p.pin_hash) <> p.pin_hash then
    raise exception 'Current PIN is incorrect';
  end if;

  update public.profiles
  set pin_enabled = false,
      pin_hash = null,
      pin_updated_at = now(),
      failed_pin_attempts = 0,
      pin_locked_until = null
  where user_id = auth.uid() and profile_index = p_profile_id;
end;
$$;

create or replace function public.clear_profile_pin_with_account_password(p_account_password text, p_profile_id integer)
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  encrypted text;
begin
  select encrypted_password into encrypted
  from auth.users
  where id = auth.uid();

  if encrypted is null or crypt(coalesce(p_account_password, ''), encrypted) <> encrypted then
    raise exception 'Invalid account password';
  end if;

  perform public.clear_profile_pin(p_profile_id, null);
end;
$$;

create or replace function public.sync_push_addons(p_profile_id integer, p_addons jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  item jsonb;
begin
  delete from public.addons where user_id = auth.uid() and profile_id = p_profile_id;
  for item in select * from jsonb_array_elements(coalesce(p_addons, '[]'::jsonb)) loop
    insert into public.addons (user_id, profile_id, url, name, enabled, sort_order)
    values (
      auth.uid(), p_profile_id, item->>'url', coalesce(item->>'name', ''),
      coalesce((item->>'enabled')::boolean, true), coalesce((item->>'sort_order')::integer, 0)
    );
  end loop;
end;
$$;

create or replace function public.sync_push_plugins(p_profile_id integer, p_plugins jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  item jsonb;
begin
  delete from public.plugins where user_id = auth.uid() and profile_id = p_profile_id;
  for item in select * from jsonb_array_elements(coalesce(p_plugins, '[]'::jsonb)) loop
    insert into public.plugins (user_id, profile_id, url, name, enabled, sort_order)
    values (
      auth.uid(), p_profile_id, item->>'url', coalesce(item->>'name', ''),
      coalesce((item->>'enabled')::boolean, true), coalesce((item->>'sort_order')::integer, 0)
    );
  end loop;
end;
$$;

create or replace function public.sync_pull_watched_items(p_profile_id integer, p_page integer default 1, p_page_size integer default 100)
returns setof public.watched_items
language sql
security definer
set search_path = public
as $$
  select *
  from public.watched_items
  where user_id = auth.uid() and profile_id = p_profile_id
  order by watched_at desc
  limit greatest(1, least(coalesce(p_page_size, 100), 500))
  offset greatest(0, coalesce(p_page, 1) - 1) * greatest(1, least(coalesce(p_page_size, 100), 500));
$$;

create or replace function public.sync_push_watched_items(p_profile_id integer, p_items jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  item jsonb;
begin
  for item in select * from jsonb_array_elements(coalesce(p_items, '[]'::jsonb)) loop
    insert into public.watched_items (user_id, profile_id, content_id, content_type, title, season, episode, watched_at)
    values (
      auth.uid(), p_profile_id, item->>'content_id', coalesce(item->>'content_type', ''),
      coalesce(item->>'title', ''), nullif(item->>'season', '')::integer,
      nullif(item->>'episode', '')::integer, coalesce((item->>'watched_at')::bigint, 0)
    )
    on conflict (user_id, profile_id, content_id, coalesce(season, -1), coalesce(episode, -1))
    do update set
      content_type = excluded.content_type,
      title = excluded.title,
      watched_at = excluded.watched_at;
  end loop;
end;
$$;

create or replace function public.sync_delete_watched_items(p_profile_id integer, p_keys jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  item jsonb;
begin
  for item in select * from jsonb_array_elements(coalesce(p_keys, '[]'::jsonb)) loop
    delete from public.watched_items
    where user_id = auth.uid()
      and profile_id = p_profile_id
      and content_id = item->>'content_id'
      and coalesce(season, -1) = coalesce(nullif(item->>'season', '')::integer, -1)
      and coalesce(episode, -1) = coalesce(nullif(item->>'episode', '')::integer, -1);
  end loop;
end;
$$;

create or replace function public.progress_key_for(p_content_id text, p_season integer, p_episode integer)
returns text
language sql
immutable
as $$
  select case
    when p_season is not null and p_episode is not null then p_content_id || '_s' || p_season || 'e' || p_episode
    else p_content_id
  end;
$$;

create or replace function public.sync_pull_watch_progress(p_profile_id integer)
returns setof public.watch_progress
language sql
security definer
set search_path = public
as $$
  select *
  from public.watch_progress
  where user_id = auth.uid() and profile_id = p_profile_id
  order by last_watched desc;
$$;

create or replace function public.sync_push_watch_progress(p_profile_id integer, p_entries jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  item jsonb;
  key text;
begin
  for item in select * from jsonb_array_elements(coalesce(p_entries, '[]'::jsonb)) loop
    key := public.progress_key_for(
      item->>'content_id',
      nullif(item->>'season', '')::integer,
      nullif(item->>'episode', '')::integer
    );

    insert into public.watch_progress (
      user_id, profile_id, content_id, content_type, video_id, season, episode,
      position, duration, last_watched, progress_key
    )
    values (
      auth.uid(), p_profile_id, item->>'content_id', coalesce(item->>'content_type', ''),
      coalesce(item->>'video_id', ''), nullif(item->>'season', '')::integer,
      nullif(item->>'episode', '')::integer, coalesce((item->>'position')::bigint, 0),
      coalesce((item->>'duration')::bigint, 0), coalesce((item->>'last_watched')::bigint, 0), key
    )
    on conflict (user_id, profile_id, progress_key) do update set
      content_type = excluded.content_type,
      video_id = excluded.video_id,
      position = excluded.position,
      duration = excluded.duration,
      last_watched = excluded.last_watched;
  end loop;
end;
$$;

create or replace function public.sync_delete_watch_progress(p_profile_id integer, p_keys jsonb)
returns void
language sql
security definer
set search_path = public
as $$
  delete from public.watch_progress
  where user_id = auth.uid()
    and profile_id = p_profile_id
    and progress_key in (select jsonb_array_elements_text(coalesce(p_keys, '[]'::jsonb)));
$$;

create or replace function public.sync_pull_library(p_profile_id integer, p_limit integer default 500, p_offset integer default 0)
returns setof public.library_items
language sql
security definer
set search_path = public
as $$
  select *
  from public.library_items
  where user_id = auth.uid() and profile_id = p_profile_id
  order by added_at desc
  limit greatest(1, least(coalesce(p_limit, 500), 1000))
  offset greatest(0, coalesce(p_offset, 0));
$$;

create or replace function public.sync_push_library(p_profile_id integer, p_items jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  item jsonb;
begin
  delete from public.library_items where user_id = auth.uid() and profile_id = p_profile_id;
  for item in select * from jsonb_array_elements(coalesce(p_items, '[]'::jsonb)) loop
    insert into public.library_items (
      user_id, profile_id, content_id, content_type, name, poster, poster_shape,
      background, description, release_info, imdb_rating, genres, added_at
    )
    values (
      auth.uid(), p_profile_id, item->>'content_id', coalesce(item->>'content_type', ''),
      coalesce(item->>'name', ''), nullif(item->>'poster', ''), coalesce(item->>'poster_shape', 'POSTER'),
      nullif(item->>'background', ''), nullif(item->>'description', ''), nullif(item->>'release_info', ''),
      nullif(item->>'imdb_rating', '')::numeric,
      coalesce(array(select jsonb_array_elements_text(coalesce(item->'genres', '[]'::jsonb))), '{}'),
      coalesce((item->>'added_at')::bigint, 0)
    );
  end loop;
end;
$$;

create or replace function public.sync_pull_collections(p_profile_id integer)
returns table (profile_id integer, collections_json jsonb, updated_at timestamptz)
language sql
security definer
set search_path = public
as $$
  select c.profile_id, c.collections_json, c.updated_at
  from public.collections c
  where c.user_id = auth.uid() and c.profile_id = p_profile_id;
$$;

create or replace function public.sync_push_collections(p_profile_id integer, p_collections_json jsonb)
returns void
language sql
security definer
set search_path = public
as $$
  insert into public.collections (user_id, profile_id, collections_json)
  values (auth.uid(), p_profile_id, coalesce(p_collections_json, '[]'::jsonb))
  on conflict (user_id, profile_id) do update set
    collections_json = excluded.collections_json,
    updated_at = now();
$$;

create or replace function public.sync_pull_profile_settings_blob(p_profile_id integer, p_platform text)
returns table (profile_id integer, settings_json jsonb, updated_at timestamptz)
language sql
security definer
set search_path = public
as $$
  select s.profile_id, s.settings_json, s.updated_at
  from public.profile_settings_blobs s
  where s.user_id = auth.uid()
    and s.profile_id = p_profile_id
    and s.platform = coalesce(p_platform, 'mobile');
$$;

create or replace function public.sync_push_profile_settings_blob(p_profile_id integer, p_platform text, p_settings_json jsonb)
returns void
language sql
security definer
set search_path = public
as $$
  insert into public.profile_settings_blobs (user_id, profile_id, platform, settings_json)
  values (auth.uid(), p_profile_id, coalesce(p_platform, 'mobile'), coalesce(p_settings_json, '{}'::jsonb))
  on conflict (user_id, profile_id, platform) do update set
    settings_json = excluded.settings_json,
    updated_at = now();
$$;

create or replace function public.sync_pull_home_catalog_settings(p_profile_id integer, p_platform text)
returns setof public.home_catalog_settings
language sql
security definer
set search_path = public
as $$
  select *
  from public.home_catalog_settings
  where user_id = auth.uid()
    and profile_id = p_profile_id
    and platform = coalesce(p_platform, 'mobile');
$$;

create or replace function public.sync_push_home_catalog_settings(p_profile_id integer, p_platform text, p_settings_json jsonb)
returns void
language sql
security definer
set search_path = public
as $$
  insert into public.home_catalog_settings (user_id, profile_id, platform, settings_json)
  values (auth.uid(), p_profile_id, coalesce(p_platform, 'mobile'), coalesce(p_settings_json, '{}'::jsonb))
  on conflict (user_id, profile_id, platform) do update set
    settings_json = excluded.settings_json,
    updated_at = now();
$$;

create or replace function public.get_avatar_catalog()
returns setof public.avatar_catalog
language sql
security definer
set search_path = public
as $$
  select *
  from public.avatar_catalog
  where is_active = true
  order by category, sort_order, display_name;
$$;

create or replace function public.watch_together_state(p_room_id uuid)
returns table (
  room_id text,
  room_code text,
  is_host boolean,
  title text,
  content_metadata jsonb,
  source_url text,
  source_headers jsonb,
  stream_title text,
  provider_name text,
  playback_state text,
  position_ms bigint,
  duration_ms bigint,
  playback_speed real,
  updated_at_ms bigint,
  server_now_ms bigint,
  member_count integer
)
language sql
security definer
set search_path = public
as $$
  select
    r.id::text,
    r.room_code,
    r.host_user_id = auth.uid(),
    r.title,
    r.content_metadata,
    r.source_url,
    r.source_headers,
    r.stream_title,
    r.provider_name,
    r.playback_state,
    r.position_ms,
    r.duration_ms,
    r.playback_speed,
    (extract(epoch from r.updated_at) * 1000)::bigint,
    (extract(epoch from now()) * 1000)::bigint,
    (
      select count(*)::integer
      from public.watch_together_members m
      where m.room_id = r.id
        and m.last_seen_at > now() - interval '2 minutes'
    )
  from public.watch_together_rooms r
  where r.id = p_room_id
    and r.closed_at is null
    and (
      r.host_user_id = auth.uid()
      or exists (
        select 1 from public.watch_together_members m
        where m.room_id = r.id and m.user_id = auth.uid()
      )
    );
$$;

create or replace function public.watch_together_create(p_profile_id integer, p_payload jsonb)
returns table (
  room_id text,
  room_code text,
  is_host boolean,
  title text,
  content_metadata jsonb,
  source_url text,
  source_headers jsonb,
  stream_title text,
  provider_name text,
  playback_state text,
  position_ms bigint,
  duration_ms bigint,
  playback_speed real,
  updated_at_ms bigint,
  server_now_ms bigint,
  member_count integer
)
language plpgsql
security definer
set search_path = public
as $$
declare
  new_room_id uuid;
  new_code text;
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  loop
    new_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6));
    exit when not exists (
      select 1
      from public.watch_together_rooms r
      where r.room_code = new_code
    );
  end loop;

  insert into public.watch_together_rooms (
    room_code, host_user_id, host_profile_id, title, content_metadata, source_url, source_headers,
    stream_title, provider_name, playback_state, position_ms, duration_ms, playback_speed
  )
  values (
    new_code,
    auth.uid(),
    coalesce(p_profile_id, 1),
    coalesce(p_payload->>'title', ''),
    coalesce(p_payload->'content_metadata', '{}'::jsonb),
    coalesce(p_payload->>'source_url', ''),
    coalesce(p_payload->'source_headers', '{}'::jsonb),
    coalesce(p_payload->>'stream_title', ''),
    coalesce(p_payload->>'provider_name', ''),
    coalesce(p_payload->>'playback_state', 'paused'),
    coalesce((p_payload->>'position_ms')::bigint, 0),
    coalesce((p_payload->>'duration_ms')::bigint, 0),
    coalesce((p_payload->>'playback_speed')::real, 1)
  )
  returning id into new_room_id;

  insert into public.watch_together_members (room_id, user_id, profile_id, display_name)
  values (new_room_id, auth.uid(), coalesce(p_profile_id, 1), 'Host')
  on conflict on constraint watch_together_members_pkey do update set last_seen_at = now();

  return query select * from public.watch_together_state(new_room_id);
end;
$$;

create or replace function public.watch_together_join(p_room_code text, p_profile_id integer, p_display_name text)
returns table (
  room_id text,
  room_code text,
  is_host boolean,
  title text,
  content_metadata jsonb,
  source_url text,
  source_headers jsonb,
  stream_title text,
  provider_name text,
  playback_state text,
  position_ms bigint,
  duration_ms bigint,
  playback_speed real,
  updated_at_ms bigint,
  server_now_ms bigint,
  member_count integer
)
language plpgsql
security definer
set search_path = public
as $$
declare
  target_room_id uuid;
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  select r.id into target_room_id
  from public.watch_together_rooms r
  where r.room_code = upper(trim(p_room_code))
    and r.closed_at is null;

  if target_room_id is null then
    raise exception 'Room not found';
  end if;

  insert into public.watch_together_members (room_id, user_id, profile_id, display_name)
  values (target_room_id, auth.uid(), coalesce(p_profile_id, 1), coalesce(nullif(trim(p_display_name), ''), 'Member'))
  on conflict on constraint watch_together_members_pkey do update set
    profile_id = excluded.profile_id,
    display_name = excluded.display_name,
    last_seen_at = now();

  return query select * from public.watch_together_state(target_room_id);
end;
$$;

create or replace function public.watch_together_get(p_room_id uuid)
returns table (
  room_id text,
  room_code text,
  is_host boolean,
  title text,
  content_metadata jsonb,
  source_url text,
  source_headers jsonb,
  stream_title text,
  provider_name text,
  playback_state text,
  position_ms bigint,
  duration_ms bigint,
  playback_speed real,
  updated_at_ms bigint,
  server_now_ms bigint,
  member_count integer
)
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.watch_together_members as m
  set last_seen_at = now()
  where m.room_id = p_room_id and m.user_id = auth.uid();

  return query select * from public.watch_together_state(p_room_id);
end;
$$;

create or replace function public.watch_together_push(p_room_id uuid, p_payload jsonb)
returns table (
  room_id text,
  room_code text,
  is_host boolean,
  title text,
  content_metadata jsonb,
  source_url text,
  source_headers jsonb,
  stream_title text,
  provider_name text,
  playback_state text,
  position_ms bigint,
  duration_ms bigint,
  playback_speed real,
  updated_at_ms bigint,
  server_now_ms bigint,
  member_count integer
)
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.watch_together_rooms as r
  set
    title = coalesce(p_payload->>'title', r.title),
    content_metadata = coalesce(p_payload->'content_metadata', r.content_metadata),
    source_url = coalesce(p_payload->>'source_url', r.source_url),
    source_headers = coalesce(p_payload->'source_headers', r.source_headers),
    stream_title = coalesce(p_payload->>'stream_title', r.stream_title),
    provider_name = coalesce(p_payload->>'provider_name', r.provider_name),
    playback_state = coalesce(p_payload->>'playback_state', r.playback_state),
    position_ms = coalesce((p_payload->>'position_ms')::bigint, r.position_ms),
    duration_ms = coalesce((p_payload->>'duration_ms')::bigint, r.duration_ms),
    playback_speed = coalesce((p_payload->>'playback_speed')::real, r.playback_speed),
    updated_at = now()
  where r.id = p_room_id
    and r.host_user_id = auth.uid()
    and r.closed_at is null;

  if not found then
    raise exception 'Only the host can control this room';
  end if;

  update public.watch_together_members as m
  set last_seen_at = now()
  where m.room_id = p_room_id and m.user_id = auth.uid();

  return query select * from public.watch_together_state(p_room_id);
end;
$$;

create or replace function public.watch_together_leave(p_room_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if exists (
    select 1 from public.watch_together_rooms r
    where r.id = p_room_id and r.host_user_id = auth.uid()
  ) then
    update public.watch_together_rooms as r
    set closed_at = now(), updated_at = now()
    where r.id = p_room_id and r.host_user_id = auth.uid();
  else
    delete from public.watch_together_members as m
    where m.room_id = p_room_id and m.user_id = auth.uid();
  end if;
end;
$$;

create or replace function public.watch_together_cleanup()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  deleted_members integer := 0;
  deleted_rooms integer := 0;
begin
  delete from public.watch_together_members as m
  where m.last_seen_at < now() - interval '1 day';
  get diagnostics deleted_members = row_count;

  delete from public.watch_together_rooms as r
  where
    (r.closed_at is not null and r.closed_at < now() - interval '1 day')
    or (
      r.closed_at is null
      and r.updated_at < now() - interval '12 hours'
      and not exists (
        select 1
        from public.watch_together_members as m
        where m.room_id = r.id
          and m.last_seen_at > now() - interval '10 minutes'
      )
    );
  get diagnostics deleted_rooms = row_count;

  return deleted_members + deleted_rooms;
end;
$$;

grant usage on schema public to anon, authenticated;
grant select, insert, update, delete on public.profiles to authenticated;
grant select, insert, update, delete on public.addons to authenticated;
grant select, insert, update, delete on public.plugins to authenticated;
grant select, insert, update, delete on public.watched_items to authenticated;
grant select, insert, update, delete on public.watch_progress to authenticated;
grant select, insert, update, delete on public.library_items to authenticated;
grant select, insert, update, delete on public.collections to authenticated;
grant select, insert, update, delete on public.profile_settings_blobs to authenticated;
grant select, insert, update, delete on public.home_catalog_settings to authenticated;
grant select, insert, update, delete on public.watch_together_rooms to authenticated;
grant select, insert, update, delete on public.watch_together_members to authenticated;
grant select on public.avatar_catalog to anon, authenticated;
grant all on public.profiles to service_role;
grant all on public.addons to service_role;
grant all on public.plugins to service_role;
grant all on public.watched_items to service_role;
grant all on public.watch_progress to service_role;
grant all on public.library_items to service_role;
grant all on public.collections to service_role;
grant all on public.profile_settings_blobs to service_role;
grant all on public.home_catalog_settings to service_role;
grant all on public.avatar_catalog to service_role;
grant all on public.watch_together_rooms to service_role;
grant all on public.watch_together_members to service_role;

grant execute on function public.sync_pull_profiles() to authenticated;
grant execute on function public.sync_push_profiles(jsonb) to authenticated;
grant execute on function public.sync_delete_profile_data(integer) to authenticated;
grant execute on function public.sync_pull_profile_locks() to authenticated;
grant execute on function public.verify_profile_pin(integer, text) to authenticated;
grant execute on function public.set_profile_pin(integer, text, text) to authenticated;
grant execute on function public.clear_profile_pin(integer, text) to authenticated;
grant execute on function public.clear_profile_pin_with_account_password(text, integer) to authenticated;
grant execute on function public.sync_push_addons(integer, jsonb) to authenticated;
grant execute on function public.sync_push_plugins(integer, jsonb) to authenticated;
grant execute on function public.sync_pull_watched_items(integer, integer, integer) to authenticated;
grant execute on function public.sync_push_watched_items(integer, jsonb) to authenticated;
grant execute on function public.sync_delete_watched_items(integer, jsonb) to authenticated;
grant execute on function public.sync_pull_watch_progress(integer) to authenticated;
grant execute on function public.sync_push_watch_progress(integer, jsonb) to authenticated;
grant execute on function public.sync_delete_watch_progress(integer, jsonb) to authenticated;
grant execute on function public.sync_pull_library(integer, integer, integer) to authenticated;
grant execute on function public.sync_push_library(integer, jsonb) to authenticated;
grant execute on function public.sync_pull_collections(integer) to authenticated;
grant execute on function public.sync_push_collections(integer, jsonb) to authenticated;
grant execute on function public.sync_pull_profile_settings_blob(integer, text) to authenticated;
grant execute on function public.sync_push_profile_settings_blob(integer, text, jsonb) to authenticated;
grant execute on function public.sync_pull_home_catalog_settings(integer, text) to authenticated;
grant execute on function public.sync_push_home_catalog_settings(integer, text, jsonb) to authenticated;
grant execute on function public.get_avatar_catalog() to anon, authenticated;
grant execute on function public.watch_together_state(uuid) to authenticated;
grant execute on function public.watch_together_create(integer, jsonb) to authenticated;
grant execute on function public.watch_together_join(text, integer, text) to authenticated;
grant execute on function public.watch_together_get(uuid) to authenticated;
grant execute on function public.watch_together_push(uuid, jsonb) to authenticated;
grant execute on function public.watch_together_leave(uuid) to authenticated;
grant execute on function public.watch_together_cleanup() to authenticated;
