-- Persist the captured waveform for each voice message.
-- Existing messages receive an empty array and render with the client's fallback waveform.
alter table public.team_messages
    add column if not exists attachment_waveform jsonb not null default '[]'::jsonb;

create or replace function private.guard_team_chat_message_waveform()
returns trigger
language plpgsql
set search_path = ''
as $function$
declare
    v_value jsonb;
    v_level numeric;
begin
    if tg_op = 'UPDATE'
       and new.attachment_waveform is distinct from old.attachment_waveform then
        raise exception 'A voice-message waveform cannot be changed after the message is sent.'
            using errcode = '42501';
    end if;

    if new.attachment_waveform is null
       or jsonb_typeof(new.attachment_waveform) is distinct from 'array'
       or jsonb_array_length(new.attachment_waveform) > 64 then
        raise exception 'A voice-message waveform must be an array of at most 64 levels.'
            using errcode = '23514';
    end if;

    for v_value in
        select element
          from jsonb_array_elements(new.attachment_waveform) as item(element)
    loop
        if jsonb_typeof(v_value) is distinct from 'number' then
            raise exception 'Voice-message waveform levels must be numeric.'
                using errcode = '23514';
        end if;
        v_level := (v_value #>> '{}')::numeric;
        if v_level < 0 or v_level > 1 then
            raise exception 'Voice-message waveform levels must be between 0 and 1.'
                using errcode = '23514';
        end if;
    end loop;

    return new;
end;
$function$;

revoke all on function private.guard_team_chat_message_waveform()
    from public, anon, authenticated;

drop trigger if exists team_message_waveform_guard on public.team_messages;
create trigger team_message_waveform_guard
before insert or update on public.team_messages
for each row
execute function private.guard_team_chat_message_waveform();
