#!/usr/bin/env bash
set -euo pipefail

mkdir -p storage/e2e
find storage/e2e -mindepth 1 -delete

export BIFF_PROFILE=prod
export BASE_URL=http://127.0.0.1:8081
export CDN_URL_TEMPLATE=http://127.0.0.1:8081/_mock/cdn/%s
export COOKIE_SECRET=MDEyMzQ1Njc4OWFiY2RlZg==
export LOCAL_MINIO=true
export MAILERSEND_API_KEY=mock
export MAILERSEND_BASE_URL=http://127.0.0.1:8081/_mock/mailersend
export MAILERSEND_DOMAIN_ID=mock-domain
export MAILERSEND_FROM=news@example.test
export MAILERSEND_PLAN=professional
export MAILERSEND_REPLY_TO=reply@example.test
export MINIO_DATA_DIR=storage/e2e/minio
export MINIO_PORT=9001
export MOCK_MAILERSEND=true
export NREPL_PORT=7889
export OBJECT_STORE_ACCESS_KEY=minioadmin
export OBJECT_STORE_ENDPOINT=http://127.0.0.1:9001
export OBJECT_STORE_SECRET_KEY=minioadmin
export PORT=8081
export SECURE=false
export SKIP_CAPTCHA=true
export SQLITE_DB_PATH=storage/e2e/main.db
export UNSUBSCRIBE_SECRET=e2e-unsubscribe-secret
export WAITLIST_ENABLED=true

exec clojure -M:run dev
