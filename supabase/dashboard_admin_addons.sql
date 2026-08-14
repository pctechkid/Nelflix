create table if not exists public.dashboard_super_admins (
  email text primary key,
  created_at timestamptz not null default now()
);

insert into public.dashboard_super_admins (email)
values ('omboyronnel@gmail.com')
on conflict (email) do nothing;

alter table public.dashboard_super_admins enable row level security;

drop policy if exists dashboard_super_admins_self_read on public.dashboard_super_admins;
create policy dashboard_super_admins_self_read on public.dashboard_super_admins
  for select to authenticated using (
    lower(email) = lower(coalesce((select u.email from auth.users u where u.id = auth.uid()), ''))
  );

create or replace function public.dashboard_is_super_admin()
returns boolean
language sql
stable
security definer
set search_path = public, auth
as $$
  select exists (
    select 1
    from public.dashboard_super_admins a
    join auth.users u on lower(u.email) = lower(a.email)
    where u.id = auth.uid()
  );
$$;

create or replace function public.dashboard_push_addons_to_all_users(p_profile_id integer)
returns table(target_users integer, source_addons integer)
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  source_user_id uuid := auth.uid();
  source_count integer := 0;
  target_count integer := 0;
begin
  if source_user_id is null then
    raise exception 'Authentication required';
  end if;

  if not public.dashboard_is_super_admin() then
    raise exception 'Super admin access required';
  end if;

  if p_profile_id is null or p_profile_id < 1 or p_profile_id > 4 then
    raise exception 'Invalid profile';
  end if;

  select count(*) into source_count
  from public.addons
  where user_id = source_user_id
    and profile_id = p_profile_id;

  select count(*) into target_count
  from auth.users
  where id <> source_user_id;

  delete from public.addons a
  using auth.users u
  where a.user_id = u.id
    and u.id <> source_user_id
    and a.profile_id = p_profile_id;

  insert into public.addons (user_id, profile_id, url, name, enabled, sort_order)
  select
    u.id,
    p_profile_id,
    source.url,
    source.name,
    source.enabled,
    source.sort_order
  from auth.users u
  cross join (
    select url, name, enabled, sort_order
    from public.addons
    where user_id = source_user_id
      and profile_id = p_profile_id
    order by sort_order, url
  ) source
  where u.id <> source_user_id;

  return query select target_count, source_count;
end;
$$;

grant select on public.dashboard_super_admins to authenticated;
grant all on public.dashboard_super_admins to service_role;
grant execute on function public.dashboard_is_super_admin() to authenticated;
grant execute on function public.dashboard_push_addons_to_all_users(integer) to authenticated;
