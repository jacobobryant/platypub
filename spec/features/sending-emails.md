# Sending emails

Every 10 minutes, a scheduled task queries for all the publications and puts
them on the "send readiness queue."

## Send readiness queue

The send readiness queue is consumed by a single thread. Each queue item has a
batch of publications. The thread determines which publications are ready for an
automatic send. A publication is ready if:

- automatic sending is enabled.
- there are no pending sends for that publication.
- the most recent send (manual or automatic) for that publication started at
  least 24 hours ago OR there are no previous sends.
- the publication has at least one visible post that (1) was fetched after the
  most recent send, manual or automatic (if one exists) AND after `automatic
  send threshold`; and (2) has `filter tag` in its tags if set; and (3) does not
  have `remove tag` in its tags if set. Tags are not case-sensitive, and
  whitespace is trimmed.
- the publication has at least one active subscriber.

For each ready publication, a send is created (with `status = pending` and
`provenance = automatic`) and placed on the send processing queue. The send
includes the posts that match the criteria described above.

If (1) `automatic send threshold` is already set and the publication's feed
changes, or (2) automatic sending is re-enabled after being disabled, then
`automatic send threshold` is set to the current time.

# Send processing queue

Emails are sent by a single thread that consumes an in-memory priority queue.
Each queue item includes a send ID. Sends with `provenance = manual` get higher
priority than sends with `provenance = automatic`. The consuming thread queries
for:

- all the active subscribers
- who subscribed before this send was created
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

## Resuming sends

We run another scheduled task every 10 minutes that queries for send entities
with `status = pending` and a `progress at` value over 15 minutes ago. These
sends are added to the send processing queue.

## Manual sending

The publication page includes a "Send" button. It is only enabled if there is at
least one post that isn't already associated with a send and if there's at least
one active subscriber. After clicking, the user selects which post(s) to include
in the send. Only posts not already associated with a send can be selected. The
user sees a preview of the full HTML that will be sent and also the from name
and subject. After the user confirms, a send is created (with `status = pending`
and `provenance = manual`) and is placed on the send processing queue. Email
content is rendered and stored at that time. In case the feed/posts were updated
after the preview was rendered, the email is rendered using the same data used
to render the preview.
