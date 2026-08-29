#!/bin/sh
# Pushes supabase/config.toml to the linked project.
#
# config.toml keeps no SMTP credentials of its own — they come from
# secrets/smtp.env, which is not committed. That is deliberate: the file used to
# carry a placeholder host, so any push quietly replaced the project's working
# SMTP settings with a dead one. Pushing without the real values now stops here
# instead.
set -e

if [ ! -f secrets/smtp.env ]; then
    echo "secrets/smtp.env is missing." >&2
    echo "It must define SMTP_HOST, SMTP_USER, SMTP_PASS and SMTP_ADMIN_EMAIL," >&2
    echo "matching what is configured on the Supabase project — pushing without" >&2
    echo "them would overwrite the project's SMTP settings." >&2
    exit 1
fi

set -a
. ./secrets/smtp.env
. ./secrets/functions.env
set +a

for var in SMTP_HOST SMTP_USER SMTP_PASS SMTP_ADMIN_EMAIL SEND_EMAIL_HOOK_SECRET; do
    eval "value=\$$var"
    if [ -z "$value" ]; then
        echo "$var is not set in secrets/ — refusing to push." >&2
        exit 1
    fi
done

exec supabase config push "$@"
