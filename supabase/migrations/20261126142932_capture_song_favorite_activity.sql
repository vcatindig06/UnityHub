create or replace function private.capture_song_favorite_activity()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_row jsonb;
    v_actor uuid;
    v_owner uuid;
    v_song_id uuid;
    v_organization_id uuid;
    v_song_title text;
    v_title text;
begin
    v_actor := auth.uid();
    if TG_OP = 'DELETE' then
        v_row := to_jsonb(OLD);
    else
        v_row := to_jsonb(NEW);
    end if;

    if v_actor is null then
        if TG_OP = 'DELETE' then return OLD; else return NEW; end if;
    end if;

    v_owner := nullif(v_row ->> 'user_id', '')::uuid;
    if v_owner is distinct from v_actor then
        if TG_OP = 'DELETE' then return OLD; else return NEW; end if;
    end if;

    v_song_id := nullif(v_row ->> 'song_id', '')::uuid;
    select song.organization_id, song.title
      into v_organization_id, v_song_title
      from public.songs as song
     where song.id = v_song_id;

    v_title := case TG_OP
        when 'INSERT' then 'Added song to favorites: ' || coalesce(v_song_title, 'Saved song')
        when 'DELETE' then 'Removed song from favorites: ' || coalesce(v_song_title, 'Saved song')
        else 'Updated a song favorite'
    end;

    insert into public.user_activity (
        actor_user_id, organization_id, event_key, title, description, entity_type, entity_id
    ) values (
        v_actor,
        v_organization_id,
        'song_favorites.' || lower(TG_OP),
        left(v_title, 180),
        '',
        'song_favorites',
        v_song_id::text
    );

    if TG_OP = 'DELETE' then return OLD; else return NEW; end if;
end;
$$;

revoke all on function private.capture_song_favorite_activity() from public, anon, authenticated;

drop trigger if exists user_activity_favorite_audit on public.song_favorites;
create trigger user_activity_favorite_audit
    after insert or delete on public.song_favorites
    for each row execute function private.capture_song_favorite_activity();
