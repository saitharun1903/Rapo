#!/usr/bin/env sh
# Creates .env from .env.example with fresh random values for every required secret, so a clean clone can run
# `docker compose up` without anyone choosing (or committing) a password. Never overwrites an existing .env.
#
# Usage (from anywhere in the repository; Git Bash on Windows works too):
#   ./scripts/init-env.sh
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
env_file="$root/.env"
example="$root/.env.example"

if [ -e "$env_file" ]; then
  echo ".env already exists; leaving it alone. Delete it first to generate new secrets." >&2
  exit 1
fi

# Letters and digits only: safe in .env, on a command line (Redis) and in SQL (the demo seed).
random() {
  LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c "$1"
}

# At least one letter and one digit: the backend refuses a demo password without both (DemoSeedConfig).
random_password() {
  while :; do
    value=$(random "$1")
    case "$value" in *[A-Za-z]*) case "$value" in *[0-9]*) printf '%s' "$value"; return ;; esac ;; esac
  done
}

database_password=$(random 32)
redis_password=$(random 32)
jwt_secret=$(random 64)
demo_password=$(random_password 24)
grafana_password=$(random 24)

# Fill only the blanks that are required; everything else keeps the example's defaults. Carriage returns are
# dropped first, in case a Windows checkout gave the example CRLF line endings.
tr -d '\r' < "$example" | sed \
  -e "s|^POSTGRES_PASSWORD=$|POSTGRES_PASSWORD=$database_password|" \
  -e "s|^DATABASE_PASSWORD=$|DATABASE_PASSWORD=$database_password|" \
  -e "s|^REDIS_PASSWORD=$|REDIS_PASSWORD=$redis_password|" \
  -e "s|^JWT_SECRET=$|JWT_SECRET=$jwt_secret|" \
  -e "s|^DEMO_USER_PASSWORD=$|DEMO_USER_PASSWORD=$demo_password|" \
  -e "s|^GRAFANA_ADMIN_PASSWORD=$|GRAFANA_ADMIN_PASSWORD=$grafana_password|" \
  > "$env_file"
chmod 600 "$env_file"

for name in POSTGRES_PASSWORD DATABASE_PASSWORD REDIS_PASSWORD JWT_SECRET DEMO_USER_PASSWORD GRAFANA_ADMIN_PASSWORD; do
  if ! grep -q "^$name=." "$env_file"; then
    echo "Could not set $name in .env; check .env.example" >&2
    exit 1
  fi
done

cat <<EOF
Wrote .env with new random secrets (git-ignored; not printed here).

Next:
  docker compose --profile demo up --build
  open http://localhost:3000
  Grafana: http://localhost:3001 (user admin, password GRAFANA_ADMIN_PASSWORD in .env)

Demo accounts (password: DEMO_USER_PASSWORD in .env):
  admin@rideflow.example.com, ananya@rideflow.example.com (passenger), driver.arjun@rideflow.example.com (driver)
EOF
