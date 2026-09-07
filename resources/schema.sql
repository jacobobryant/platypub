-- Auto-generated; do not edit.

CREATE TABLE biff_sqlite_kv (
  id BLOB PRIMARY KEY NOT NULL,
  k TEXT NOT NULL,
  namespace TEXT NOT NULL,
  v BLOB NOT NULL,
  UNIQUE(namespace, k)
) STRICT;

CREATE TABLE tab_state (
  id BLOB PRIMARY KEY NOT NULL,
  data BLOB
) STRICT;

CREATE TABLE user (
  id BLOB PRIMARY KEY NOT NULL,
  email TEXT NOT NULL,
  joined_at INT NOT NULL,
  display_name TEXT,
  UNIQUE(email)
) STRICT;

CREATE INDEX idx_user_joined_at ON user(joined_at);