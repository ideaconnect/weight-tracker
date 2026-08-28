-- Cloud backup: one row per user holding the whole snapshot as JSON. The app
-- upserts it whenever backup is on and something changed; restore reads it
-- back; "clear backed-up data" deletes the row. updated_at is server time and
-- is what the Settings screen shows as "Last backup".

create table if not exists public.backups (
    user_id uuid primary key references auth.users (id) on delete cascade,
    payload jsonb not null,
    updated_at timestamptz not null default now()
);

alter table public.backups enable row level security;

drop policy if exists "own backup select" on public.backups;
drop policy if exists "own backup insert" on public.backups;
drop policy if exists "own backup update" on public.backups;
drop policy if exists "own backup delete" on public.backups;
create policy "own backup select" on public.backups for select using (auth.uid() = user_id);
create policy "own backup insert" on public.backups for insert with check (auth.uid() = user_id);
create policy "own backup update" on public.backups for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own backup delete" on public.backups for delete using (auth.uid() = user_id);

create or replace function public.touch_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end $$;

drop trigger if exists backups_touch on public.backups;
create trigger backups_touch before update on public.backups
    for each row execute function public.touch_updated_at();

-- Captured auth emails (see functions/auth-mail-hook). RLS on, no policies:
-- only the service role — meaning the two edge functions — can touch it.
create table if not exists public.auth_mail (
    id bigint generated always as identity primary key,
    created_at timestamptz not null default now(),
    email text not null,
    new_email text,
    action_type text not null,
    token text not null,
    token_hash text,
    token_new text
);

alter table public.auth_mail enable row level security;
revoke all on table public.auth_mail from anon, authenticated;

-- A signed-in user deletes their own account. Security definer because
-- auth.users is not otherwise reachable; cascades take the backup row with it.
create or replace function public.delete_user()
returns void language sql security definer set search_path = '' as
$$ delete from auth.users where id = auth.uid() $$;

revoke all on function public.delete_user() from public, anon;
grant execute on function public.delete_user() to authenticated;
