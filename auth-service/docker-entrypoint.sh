#!/bin/sh
set -eu

mkdir -p /run/app-secrets
cp /run/source-secrets/jwt-private.pem /run/app-secrets/jwt-private.pem
cp /run/source-secrets/jwt-public.pem /run/app-secrets/jwt-public.pem
chown -R 10001:10001 /run/app-secrets
chmod 400 /run/app-secrets/jwt-private.pem
chmod 444 /run/app-secrets/jwt-public.pem

exec su-exec 10001:10001 "$@"
