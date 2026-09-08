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
ID updated at` value.

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

## Subscribe form

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

## Sending emails

Every 10 minutes, a scheduled task queries for all the publications and puts
them on the "send readiness queue."

#### Send readiness queue

The send readiness queue is consumed by a single thread. Each queue item has a
batch of publications. The thread determines which publications are ready for an
automatic send. A publication is ready if:

- automatic sending is enabled.
- the most recent send for that publication started at least 24 hours ago.
- the publication has at least one post that was fetched after the most recent
  send.
- the publication has at least one eligible subscriber.

For each ready publication, a send is created (with `status = pending` and
`provenance = automatic`) and placed on the send processing queue. The send
includes all posts for the publication that were fetched after the most recent
prior send.

### Send processing queue

Emails are sent by a single thread that consumes an in-memory priority queue.
Each queue item includes a send ID. Sends with `provenance = manual` get higher
priority than sends with `provenance = automatic`. The consuming thread queries
for all the eligible subscribers that do not yet have a send attempt for this
send and then sends them an email with our email service provider's API. Prior
to each API call, we create a send attempt entity for each subscriber included
in the API call. This ensures "at most once" processing of send attempts.

The thread must be sure to respect API rate limits. When creating send attempt
entities, we also update the send's `last sent at` attribute. After all the send
attempts have been made, we change the send's `status` to `finished`.

### Resuming sends

We run another scheduled task every 10 minutes that queries for send entities
with `status = pending` and a `last sent at` value over 15 minutes ago. These
sends are added to the send processing queue.

### Manual sending

The publication page includes a "Send" button. It is only enabled if there is at
least one post that hasn't already been sent. After clicking, the user selects
which post(s) to include in the send. Only posts not already sent can be
selected. The user sees a preview of the full HTML that will be sent and also
the from name and subject. After the user confirms, a send is created (with
`status = pending` and `provenance = manual`) and is placed on the send
processing queue.

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

### Matching posts

When fetching feeds, a post in the feed matches a post entity we've already
created if any of the attributes `GUID`, `title`, or `URL` are non-empty and
have the same values. When a post matches, we update any fields that have
changed instead of creating a new post. We do not update `present as of`; that
attribute is only updated in response to a publication being created/updated.

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

## Notes

No two sends for the same publication may share a post ID. Sends must always be
created in a transaction that enforces this invariant.
