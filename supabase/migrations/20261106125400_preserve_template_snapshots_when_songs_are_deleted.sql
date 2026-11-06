-- Deleting a library song must not destroy a reusable setlist template.
-- The template keeps the original ChordPro snapshot and simply loses the live song link.
alter table public.service_template_items
    drop constraint service_template_items_song_id_fkey;

alter table public.service_template_items
    add constraint service_template_items_song_id_fkey
    foreign key (song_id)
    references public.songs(id)
    on delete set null;
