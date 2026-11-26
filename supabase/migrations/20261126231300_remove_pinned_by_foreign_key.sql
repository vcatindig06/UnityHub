-- Keep pinned message history independent of auth-user deletion cascades.
-- The pinned_by value is informational; authorization is checked against the
-- active auth.uid() and organization role in the message update trigger.
alter table public.team_messages
    drop constraint if exists team_messages_pinned_by_fkey;
