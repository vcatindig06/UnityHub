-- Allow signed-in members to update only their own notification read timestamp.
-- The existing notifications_mark_own_read RLS policy restricts row ownership.
-- No content, recipient, organization, or notification-type columns are writable.

grant update (read_at) on table public.notifications to authenticated;
