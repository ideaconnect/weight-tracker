-- Account email is delivered for real now (Resend SMTP, [auth.hook.send_email]
-- disabled), so nothing writes here and nothing reads it. The table existed only
-- so the E2E suite could read verification codes; it asks GoTrue to mint them
-- instead, which sends nothing.
--
-- It held live signup and password-reset codes in plain text, which is not a
-- thing to leave lying around on a project that has real users.
drop table if exists public.auth_mail;
