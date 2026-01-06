#!/usr/bin/env bash
# Load variables from .env and run Gradle bootRun
ENV_FILE="${1:-.env}"
if [ ! -f "$ENV_FILE" ]; then
  echo "$ENV_FILE not found. Copy .env.example to $ENV_FILE and fill values."
  exit 1
fi

# Export variables (skip comments and empty lines)
set -a
grep -v '^\s*#' "$ENV_FILE" | sed -n 's/^[[:space:]]*//;s/[[:space:]]*$//;/^$/d;/=/p' | while IFS='=' read -r name value; do
  # remove surrounding quotes if present
  value="${value%\"}"
  value="${value#\"}"
  export "$name=$value"
  # Debug: mask sensitive values when printing
  lower_name=$(echo "$name" | tr '[:upper:]' '[:lower:]')
  if [[ "$lower_name" == *password* || "$lower_name" == *secret* || "$lower_name" == *jwt* || "$lower_name" == *token* || "$lower_name" == *key* ]]; then
    display="****"
  else
    display="$value"
  fi
  echo "Loaded $name=$display"
done
set +a

echo "Environment loaded from $ENV_FILE"
./gradlew bootRun
