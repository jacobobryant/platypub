-- Auto-generated; do not edit.

CREATE TABLE biff_sqlite_kv (
  id BLOB PRIMARY KEY NOT NULL,
  k TEXT NOT NULL,
  namespace TEXT NOT NULL,
  v BLOB NOT NULL,
  UNIQUE(namespace, k)
) STRICT;

CREATE TABLE feed (
  id BLOB PRIMARY KEY NOT NULL,
  created_at INT NOT NULL,
  failed_syncs INT NOT NULL,
  fetched_at INT NOT NULL,
  url TEXT NOT NULL,
  etag TEXT,
  last_modified TEXT,
  UNIQUE(url)
) STRICT;

CREATE TABLE tab_state (
  id BLOB PRIMARY KEY NOT NULL,
  data BLOB
) STRICT;

CREATE TABLE user (
  id BLOB PRIMARY KEY NOT NULL,
  email TEXT NOT NULL,
  joined_at INT NOT NULL,
  tier INT NOT NULL CHECK (tier IN (0, 1, 2)), -- waitlist (0), free (1), admin (2)
  display_name TEXT,
  UNIQUE(email)
) STRICT;

CREATE TABLE post (
  id BLOB PRIMARY KEY NOT NULL,
  feed_id BLOB NOT NULL,
  fetched_at INT NOT NULL,
  present_as_of INT NOT NULL,
  author_image_url TEXT,
  author_name TEXT,
  author_url TEXT,
  content_hash TEXT,
  content_id BLOB,
  excerpt TEXT,
  guid TEXT,
  length INT,
  published_at INT,
  tags BLOB,
  title TEXT,
  url TEXT,
  FOREIGN KEY(feed_id) REFERENCES feed(id)
) STRICT;

CREATE TABLE publication (
  id BLOB PRIMARY KEY NOT NULL,
  background_color TEXT NOT NULL,
  created_at INT NOT NULL,
  feed_id BLOB NOT NULL,
  feed_id_updated_at INT NOT NULL,
  padding_color TEXT NOT NULL,
  primary_color TEXT NOT NULL,
  require_confirmation INT NOT NULL,
  text_color TEXT NOT NULL,
  title TEXT NOT NULL,
  user_id BLOB NOT NULL,
  welcome_html TEXT NOT NULL,
  address TEXT,
  archived_at INT,
  automatic_send_threshold INT,
  banner_image_url TEXT,
  default_author_image_url TEXT,
  default_author_name TEXT,
  default_author_url TEXT,
  description TEXT,
  filter_tag TEXT,
  intro TEXT,
  remove_tag TEXT,
  FOREIGN KEY(feed_id) REFERENCES feed(id),
  FOREIGN KEY(user_id) REFERENCES user(id)
) STRICT;

CREATE TABLE send (
  id BLOB PRIMARY KEY NOT NULL,
  content_id BLOB NOT NULL,
  from_name TEXT NOT NULL,
  progress_at INT NOT NULL,
  provenance INT NOT NULL CHECK (provenance IN (0, 1)), -- manual (0), automatic (1)
  publication_id BLOB NOT NULL,
  reply_to TEXT NOT NULL,
  started_at INT NOT NULL,
  status INT NOT NULL CHECK (status IN (0, 1)), -- pending (0), finished (1)
  subject TEXT NOT NULL,
  FOREIGN KEY(publication_id) REFERENCES publication(id)
) STRICT;

CREATE TABLE subscriber (
  id BLOB PRIMARY KEY NOT NULL,
  email TEXT NOT NULL,
  publication_id BLOB NOT NULL,
  require_confirmation INT NOT NULL,
  subscribed_at INT NOT NULL,
  confirmation_token BLOB,
  confirmation_triggered_at INT,
  confirmed_at INT,
  form_params BLOB,
  headers BLOB,
  query_params BLOB,
  suppressed INT,
  unsubscribed_at INT,
  FOREIGN KEY(publication_id) REFERENCES publication(id),
  UNIQUE(email, publication_id)
) STRICT;

CREATE TABLE send_attempt (
  id BLOB PRIMARY KEY NOT NULL,
  send_id BLOB NOT NULL,
  subscriber_id BLOB NOT NULL,
  FOREIGN KEY(send_id) REFERENCES send(id),
  FOREIGN KEY(subscriber_id) REFERENCES subscriber(id),
  UNIQUE(send_id, subscriber_id)
) STRICT;

CREATE TABLE send_post (
  id BLOB PRIMARY KEY NOT NULL,
  post_id BLOB NOT NULL,
  send_id BLOB NOT NULL,
  FOREIGN KEY(post_id) REFERENCES post(id),
  FOREIGN KEY(send_id) REFERENCES send(id)
) STRICT;

CREATE INDEX idx_feed_fetched_at ON feed(fetched_at);
CREATE INDEX idx_post_feed_id ON post(feed_id);
CREATE INDEX idx_post_fetched_at ON post(fetched_at);
CREATE INDEX idx_publication_feed_id ON publication(feed_id);
CREATE INDEX idx_publication_user_id ON publication(user_id);
CREATE INDEX idx_send_progress_at ON send(progress_at);
CREATE INDEX idx_send_publication_id ON send(publication_id);
CREATE INDEX idx_send_attempt_send_id ON send_attempt(send_id);
CREATE INDEX idx_send_post_send_id ON send_post(send_id);
CREATE INDEX idx_subscriber_email ON subscriber(email);
CREATE INDEX idx_subscriber_publication_id ON subscriber(publication_id);
CREATE INDEX idx_subscriber_subscribed_at ON subscriber(subscribed_at);
CREATE INDEX idx_user_joined_at ON user(joined_at);

CREATE INDEX IF NOT EXISTS idx_post_feed_sort ON post(feed_id, fetched_at, published_at);
CREATE INDEX IF NOT EXISTS idx_subscriber_publication_date ON subscriber(publication_id, subscribed_at);
CREATE INDEX IF NOT EXISTS idx_send_publication_date ON send(publication_id, started_at);
CREATE TRIGGER IF NOT EXISTS prevent_duplicate_publication_post BEFORE INSERT ON send_post WHEN EXISTS (SELECT 1 FROM send_post previous JOIN send previous_send ON previous_send.id = previous.send_id JOIN send new_send ON new_send.id = NEW.send_id WHERE previous.post_id = NEW.post_id AND previous_send.publication_id = new_send.publication_id) BEGIN SELECT RAISE(ABORT, 'post already sent for publication'); END;