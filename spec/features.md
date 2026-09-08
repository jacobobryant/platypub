# Platypub MVP

This is a lightweight publishing app. It lets people add email newsletter
functionality to an existing blog that they host elsewhere.

## Signup

The first user to sign up is put on the "admin" tier. The admin has access to a
dashboard that lets them edit the tier of other users (and themselves).

An environment config setting controls whether the app has a waitlist. If set,
new users (other than the admin user) are put on the "waitlist" tier. Waitlist
users are not able to use the app. When they sign in, they see only a message
saying that they're on the waitlist and will be notified when they are given
access to the application.

## Create a publication

A user can have multiple publications. To create a publication, they first enter
a website or feed URL. If it's a website URL, we try to infer the associated
feed and show an error message if we couldn't find one. If there are multiple
feed URLs, we ask the user which they want to use.

After we have a feed URL, we verify that we are able to parse it successfully
and that it contains at least one post. If so, we check if there is already a
feed entity with the same URL. If so we use that feed ID; otherwise we create a
new feed. We save/update posts from the feed. Then we create a publication. The
feeds posts' `present as of` is set to the same value as the publication's `feed
ID updated at` value (only for posts in the feed response we just fetched).

We attempt to use values from the feed to populate these publication attributes:

- title
- description
- intro (same as description)
- default author name
- default author URL
- default author image URL

For the author information, we look only at the overall feed information, not
individual post information.

A new publication starts with zero subscribers. Importing subscribers is not
supported for free users. Users can change publication settings after creating
it:

- feed URL (we convert this to a feed ID transparently, fetching the feed and
  saving/updating posts using the same logic as when creating the publication)
- automatic sending
- require confirmation
- title, description, intro
- banner image
- default author info
- color settings
- filter/remove tag
- welcome html (we show a text area with raw html)

For images, we display the image rather than the URL. For changing the image, we
show an upload form and record the object storage CDN URL.

While editing the publication settings, the user is shown a preview of the
subscribe form and an email with Lorem Ipsum content.

When a publication is created or its feed is changed, we sync the feed
immediately on the HTTP request thread as described above. For any post entities
in the feed that we've already created, we update `present as of` to the current
time.

## Importing subscribers

Admin users have a dashboard where they can search for a publication and then
import subscribers for a selected publication. Publications are not shown until
the admin enters a search query.

## Viewing posts

After signing in, users see a list of their publications. After navigating to a
particular publication, they see a list of posts for that publication (the
"publication page"). Posts are shown if:

- they were previously sent to subscribers, OR
    - they are part of the publication's current feed, AND
    - the post's `present as of` value is >= the publication's `feed ID updated
      at` value.

This spec guarantees that a publication always has at least one post since
you cannot set a publication's feed to a feed that doesn't have at least one
post.

Posts are sorted (first by `fetched at`, then by `published at`, then by ID) and
paginated (20 per page). The list view shows if and when a post was sent (the
send's `started at` value). There is no page to view an individual post.

## Subscribing

The publication page shows a URL for the subscribe form (hosted page, with
publication ID as a URL path param) and an html snippet for embedding the
subscribe form. The subscribe form is captcha protected with cloudflare
turnstile, falling back to hcaptcha if the user is blocking cloudflare.

Email addresses are normalized by trimming whitespace from the ends and
converting to lower case.

- If the email address is not already associated with a subscriber entity for
  this publication, we create a subscriber entity and set `require confirmation`
  to the value of the publication's `require confirmation` attribute.
- If the subscriber entity already exists, `unsubscribed at` is set, and
  `suppressed` is not set, then clear the `unsubscribed at` and `confirmed at`
  attributes and set `require confirmation = true`.
- Otherwise, do not update the database.

In all cases, the subscribe form should show the same message afterward (even if
that message is inaccurate, like "we've sent you a confirmation email") since we
don't want to expose any information about the subscriber's state.

### Confirmation emails

A confirmation email is sent when:

- someone successfully submits the signup form
- `suppressed` is not set
- `require confirmation` is true
- `confirmed at` is not set

When sending a confirmation email, a random token is stored in `confirmation
token` and `confirmation triggered at` is set to the current time. The
confirmation email includes a link with the token embedded in a path parameter
(email clients sometimes remove or alter query parameters). The token is valid
for 24 hours. If the user clicks the link and the token is still valid,
`confirmed at` is set to the current time, and `confirmation triggered at` and
`confirmation token` are both cleared.

### Welcome email

A welcome email is sent when someone who was not previously an active subscriber
becomes an active subscriber.

### Unsubscribing

Send emails (i.e. not welcome or confirmation emails) always include an
unsubscribe link at the bottom. They also set whatever unsubscribe headers are
allowed by the email provider for the current plan. When a user unsubscribes,
`unsubscribe at` is set to the current time.

## Sending emails

Every 10 minutes, a scheduled task queries for all the publications and puts
them on the "send readiness queue."

#### Send readiness queue

The send readiness queue is consumed by a single thread. Each queue item has a
batch of publications. The thread determines which publications are ready for an
automatic send. A publication is ready if:

- automatic sending is enabled.
- the most recent send (manual or automatic) for that publication started at
  least 24 hours ago OR there are no previous sends.
- the publication has at least one visible post that (1) was fetched after the
  most recent send, manual or automatic (if one exists) AND after `automatic
  sending threshold`; and (2) has `filter tag` in its tags if set; and (3) does
  not have `remove tag` in its tags if set. Tags are not case-sensitive, and
  whitespace is trimmed.
- the publication has at least one active subscriber.

For each ready publication, a send is created (with `status = pending` and
`provenance = automatic`) and placed on the send processing queue. The send
includes the posts that match the criteria described above.

`automatic send threshold` should be bumped to the current time when a
publication's feed changes and when automatic sending is re-enabled after being
disabled.

### Send processing queue

Emails are sent by a single thread that consumes an in-memory priority queue.
Each queue item includes a send ID. Sends with `provenance = manual` get higher
priority than sends with `provenance = automatic`. The consuming thread queries
for:

- all the active subscribers
- who joined before this send was created
- and who do not yet have a send attempt for this send

The thread then sends each subscriber an email with our email service provider's
API. Prior to each API call, we create a send attempt entity for each subscriber
included in the API call. This ensures "at most once" processing of send
attempts.

The thread must be sure to respect API rate limits, for example, by backing off
and retrying, and also by throttling our requests to avoid hitting rate limits
in the first place. When creating send attempt entities, we also update the
send's `progress at` attribute. After all the send attempts have been made, we
change the send's `status` to `finished`.

API errors not related to rate limits should result in the send attempts being
skipped (with the entities still in the database) instead of retried. These
errors should be logged.

### Resuming sends

We run another scheduled task every 10 minutes that queries for send entities
with `status = pending` and a `progress at` value over 15 minutes ago. These
sends are added to the send processing queue.

### Manual sending

The publication page includes a "Send" button. It is only enabled if there is at
least one post that hasn't already been sent. After clicking, the user selects
which post(s) to include in the send. Only posts not already sent can be
selected. The user sees a preview of the full HTML that will be sent and also
the from name and subject. After the user confirms, a send is created (with
`status = pending` and `provenance = manual`) and is placed on the send
processing queue. Email content is rendered and stored at that time.
In case the feed/posts were updated after the preview was rendered, the email is
rendered using the same data used to render the preview.

## Feed syncing

Every 15 minutes, a scheduled task queries for all the feeds that are ready to
be fetched again. A feed is ready if it is currently attached to a publication
and the elapsed time since the last fetch has passed a threshold based on
`failed syncs`:

- 0 failed syncs: 15 minutes
- 1 failed sync: 1 hour
- 2 failed syncs: 3 hours
- 3 failed syncs: 6 hours
- 4 failed syncs: 12 hours
- 5+ failed syncs: 24 hours

Feeds ready for fetching are placed on a queue that's consumed by a pool of 4
threads. The thread pool fetches feeds and updates the feed and post entities.
Feeds are fetched using the `etag` and `last modified` attributes/headers
properly to avoid requesting data unnecessarily. Timeout setting for the HTTP
client library are also set appropriately. We set the user agent to `platypub`.
After a feed is fetched, its publication is placed on the send readiness queue.

On the publication page, there is a "Sync feed" button that puts the
publication's feed on the queue immediately with a priority higher than the
feeds put on the queue by the scheduled task.

### Post content

When parsing a feed, if there is no content set but there is a URL, then the
content is `<a href="{URL}">URL</a>`. If there is no content and no URL, skip
the post. If the feed does not have any posts with URL or content set, the feed
does not count as having any posts and is invalid when trying to set a
publication's feed to it.

### Matching posts

When fetching feeds, a post in the feed matches a post entity we've already
created if:

- the posts have the same feed
- `GUID` is set and it matches, OR
- `GUID` is not set, but `URL` is set and it matches, OR
- `GUID` and `URL` are not set but `content hash` matches.

any of the attributes `GUID`, `title`, or `URL` are non-empty and have the same
values. When a post matches, we update any fields that have changed instead of
creating a new post. We do not update `present as of` for existing posts; that
attribute is only updated in response to a publication being created/updated. We
do set `present as of` to `fetched at` when creating posts.

## Subscriber management

Each publication page has a "subscribers" button that takes you to a paginated,
searchable page showing the subscribers for that publication, more recent
subscribers first. The subscribers are shown on a table with columns for email,
subscribed at, and unsubscribed at. No other data is shown. There is a dropdown
for each row with a single "unsubscribe" button. It is gated by a confirmation
modal that explains the action cannot be undone. Each row also has a checkbox so
you can select multiple subscribers and unsubscribe them at once (e.g. in case
you get a bunch of spam subscribes). The table has a sticky header row with an
unsubscribe button that becomes enabled when you have at least one subscriber
selected.

## Email providers

The only supported email provider is https://mailersend.com. Include an
environment config setting to specify what plan is being used, since some
features we want to use may only be available on certain plans (like the ability
to set unsubscribe headers).

## Complaint and bounce handling

Every 6 hours, a scheduled task should use the email provider's API to get a
list of email addresses who have complained or hard bounced in the past 24
hours. `suppressed` should be set for these email addresses on all publications.

## Email rendering

Posts in an email are ordered first by `fetched at` (oldest first), then by
`published at` (oldest first, unset last), then by post ID (ordering doesn't
matter as long as its stable).

The From address is hardcoded in the email provider settings (on their website)
or via an environment config setting if we need to include a value in our API
calls. The From name and Reply-To name are the publication title. The Reply-To
address is the user's `email`. The subject is the title of the first post that
has a title. If no posts have a title, the subject is the first 40 characters of
the first post's content, with an ellipsis if the content is longer than 40
characters.

Each post is rendered with its title, URL, author information, and
content/excerpt. If there is only one post, then we render the post's full
content. If there are multiple posts, we render the `excerpt` instead of the
full content and we also render `published at` if set.

If there is only one post and it has a URL, the URL is rendered as a "Read
online" link. If there are multiple posts, each post's URL is rendered as the
link target for `published at` if set and as a "Read online" link if not.

If a post doesn't have an `author name` and the publication does have a `default
author name`, the post uses the `default author name`, `default author URL`, and
`default image URL` from the publication as its author information. The defaults
are all-or-nothing; the publication's `default author name` would not be used
with the post's `author URL` for example. If the post doesn't have an author
name (whether from the post or the publication), the other author information is
ignored.

If there are multiple posts, author information is rendered along with each
post. Except that if each post has the same author information, the author
information is rendered once at the top of the email.

The intro is rendered in italics if set.

## Notes

No two sends for the same publication may share a post ID. Sends must always be
created in a transaction that enforces this invariant.
