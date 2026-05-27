alter table public.watch_together_rooms
  add column if not exists content_metadata jsonb not null default '{}'::jsonb;

drop function if exists public.watch_together_leave(uuid);
drop function if exists public.watch_together_push(uuid, jsonb);
drop function if exists public.watch_together_get(uuid);
drop function if exists public.watch_together_join(text, integer, text);
drop function if exists public.watch_together_create(integer, jsonb);
drop function if exists public.watch_together_state(uuid);

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
    where r.id = p_room_id;
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

grant execute on function public.watch_together_state(uuid) to authenticated;
grant execute on function public.watch_together_create(integer, jsonb) to authenticated;
grant execute on function public.watch_together_join(text, integer, text) to authenticated;
grant execute on function public.watch_together_get(uuid) to authenticated;
grant execute on function public.watch_together_push(uuid, jsonb) to authenticated;
grant execute on function public.watch_together_leave(uuid) to authenticated;
grant execute on function public.watch_together_cleanup() to authenticated;
