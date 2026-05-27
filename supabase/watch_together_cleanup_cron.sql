do $$
begin
  if to_regnamespace('cron') is null then
    raise notice 'Supabase Cron is not enabled. Enable Integrations > Cron, then run this script again.';
    return;
  end if;

  begin
    execute 'select cron.unschedule(''nelflix-watch-together-cleanup'')';
  exception
    when others then null;
  end;

  execute $cron$
    select cron.schedule(
      'nelflix-watch-together-cleanup',
      '*/15 * * * *',
      'select public.watch_together_cleanup();'
    )
  $cron$;
end;
$$;
