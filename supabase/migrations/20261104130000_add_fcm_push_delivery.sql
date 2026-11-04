-- UnityHub FCM push delivery. Applied to Supabase production before deployment.
-- The Android client stores only its own FCM token; Firebase service credentials stay server-side.

create extension if not exists pg_net with schema extensions;

create table if not exists public.device_push_tokens (
    token text primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    platform text not null default 'android' check (platform = 'android'),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists device_push_tokens_user_id_idx
    on public.device_push_tokens (user_id);

alter table public.device_push_tokens enable row level security;
revoke all on table public.device_push_tokens from anon, authenticated;
grant select, insert, update, delete on table public.device_push_tokens to authenticated;
grant all on table public.device_push_tokens to service_role;

drop policy if exists device_push_tokens_read_own on public.device_push_tokens;
create policy device_push_tokens_read_own
    on public.device_push_tokens for select to authenticated
    using ((select auth.uid()) = user_id);

drop policy if exists device_push_tokens_insert_own on public.device_push_tokens;
create policy device_push_tokens_insert_own
    on public.device_push_tokens for insert to authenticated
    with check ((select auth.uid()) = user_id);

drop policy if exists device_push_tokens_update_own on public.device_push_tokens;
create policy device_push_tokens_update_own
    on public.device_push_tokens for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

drop policy if exists device_push_tokens_delete_own on public.device_push_tokens;
create policy device_push_tokens_delete_own
    on public.device_push_tokens for delete to authenticated
    using ((select auth.uid()) = user_id);

do $$
begin
    if not exists (select 1 from vault.secrets where name = 'unityhub_notification_webhook_secret') then
        perform vault.create_secret(
            encode(extensions.gen_random_bytes(32), 'hex'),
            'unityhub_notification_webhook_secret',
            'HMAC key for authenticating UnityHub notification webhook requests'
        );
    end if;

    if not exists (select 1 from vault.secrets where name = 'unityhub_notification_webhook_apikey') then
        perform vault.create_secret(
            'sb_publishable_UZTK6hooA0PiqvEXyeL7Nw_yN7_V3Ew',
            'unityhub_notification_webhook_apikey',
            'Publishable API key used for the UnityHub notification webhook request'
        );
    end if;
end
$$;

create or replace function public.verify_notification_push_webhook(
    p_notification_id uuid,
    p_signature text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_secret text;
    v_expected text;
begin
    if p_notification_id is null or p_signature is null or p_signature !~ '^[0-9a-f]{64}$' then
        return false;
    end if;

    select decrypted_secret into v_secret
      from vault.decrypted_secrets
     where name = 'unityhub_notification_webhook_secret';

    if v_secret is null then
        return false;
    end if;

    v_expected := encode(
        extensions.hmac(
            convert_to(p_notification_id::text, 'UTF8'),
            convert_to(v_secret, 'UTF8'),
            'sha256'
        ),
        'hex'
    );

    return v_expected = p_signature;
end;
$$;

revoke all on function public.verify_notification_push_webhook(uuid, text) from public, anon, authenticated;
grant execute on function public.verify_notification_push_webhook(uuid, text) to service_role;

create or replace function public.dispatch_notification_push()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_secret text;
    v_apikey text;
    v_signature text;
begin
    select decrypted_secret into v_secret
      from vault.decrypted_secrets
     where name = 'unityhub_notification_webhook_secret';

    select decrypted_secret into v_apikey
      from vault.decrypted_secrets
     where name = 'unityhub_notification_webhook_apikey';

    if v_secret is null or v_apikey is null then
        raise warning 'UnityHub notification webhook secrets are not configured';
        return new;
    end if;

    v_signature := encode(
        extensions.hmac(
            convert_to(new.id::text, 'UTF8'),
            convert_to(v_secret, 'UTF8'),
            'sha256'
        ),
        'hex'
    );

    perform net.http_post(
        url := 'https://jbcxqeuvzdwkaetaipln.supabase.co/functions/v1/send-notification-push',
        body := jsonb_build_object('notification_id', new.id),
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'apikey', v_apikey,
            'x-unityhub-signature', v_signature
        ),
        timeout_milliseconds := 5000
    );

    return new;
exception
    when others then
        raise warning 'UnityHub push webhook enqueue failed: %', SQLERRM;
        return new;
end;
$$;

revoke all on function public.dispatch_notification_push() from public, anon, authenticated;

drop trigger if exists dispatch_notification_push_after_insert on public.notifications;
create trigger dispatch_notification_push_after_insert
    after insert on public.notifications
    for each row
    execute function public.dispatch_notification_push();
