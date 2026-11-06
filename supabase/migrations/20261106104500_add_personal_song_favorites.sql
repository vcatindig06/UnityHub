-- Personal favorites are private to each signed-in member.
-- A member can only favorite songs in an organization they currently belong to.

create table public.song_favorites (
    user_id uuid not null references auth.users(id) on delete cascade,
    song_id uuid not null references public.songs(id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint song_favorites_pkey primary key (user_id, song_id)
);

create index song_favorites_song_id_idx
    on public.song_favorites (song_id);

alter table public.song_favorites enable row level security;

create policy song_favorites_select_own_team_songs
    on public.song_favorites
    for select
    to authenticated
    using (
        user_id = (select auth.uid())
        and exists (
            select 1
            from public.songs as song
            where song.id = song_favorites.song_id
              and private.is_org_member(song.organization_id)
        )
    );

create policy song_favorites_insert_own_team_songs
    on public.song_favorites
    for insert
    to authenticated
    with check (
        user_id = (select auth.uid())
        and exists (
            select 1
            from public.songs as song
            where song.id = song_favorites.song_id
              and private.is_org_member(song.organization_id)
        )
    );

create policy song_favorites_delete_own_team_songs
    on public.song_favorites
    for delete
    to authenticated
    using (
        user_id = (select auth.uid())
        and exists (
            select 1
            from public.songs as song
            where song.id = song_favorites.song_id
              and private.is_org_member(song.organization_id)
        )
    );

-- Public-schema Data API grants are opt-in for newly created tables.
revoke all on table public.song_favorites from anon;
revoke all on table public.song_favorites from public;
revoke all on table public.song_favorites from authenticated;
grant select, insert, delete on table public.song_favorites to authenticated;
