-- Supabase projects may still have broad default privileges on newly created tables.
-- Revoke them and grant only the operations used by the personal favorites feature.
revoke all on table public.song_favorites from authenticated;
grant select, insert, delete on table public.song_favorites to authenticated;
