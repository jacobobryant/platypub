# Data model

Notes:

- Each user entity implicitly has an ID of type UUID v7.

## User

Required:

- `joined at`
- `email`. Unique.
- `tier` (enum: waitlist, free, admin)

## Publication

Users create publications by entering a feed URL. A publication has an
associated subscribe form. People who subscribe via that subscribe form receive
emails containing the posts from the feed.

Required:

- `created at`
- `user ID`.
- `feed ID`. The feed containing the content for this publication. This can
  change.
- `feed ID updated at`
- `title`. Text used on the subscribe form and as the "From" name on emails.
  When creating a publication, if the feed doesn't have a title, this defaults
  to the feed URL.
- `padding color`. Color to use for the space around email content and around
  the subscribe form. Default off-white.
- `background color`. Color to use for the backgrund of email content and the
  subscribe form. Default white.
- `text color`. Text color to use in emails and on the subscribe form. Default
  black.
- `primary color`. Color to use for links and buttons in emails and on the
  subscribe form. Default blue.
- `welcome html`. Content for the welcome email. Defaults to "Thanks for
  subscribing."
- `require confirmation`. If true, new subscribers must confirm their
  subscription by clicking a link in an email ("double opt-in"). Default false.
- `address`. This is rendered in send emails next to the unsubscribe link.
  Sending is not allowed if this value is blank.

Optional:

- `automatic send threshold`. When set, posts fetched after this time
  are sent automatically. Default `created at`. When set, automatic sending is
  enabled.
- `description`. Text shown on the subscribe form.
- `intro`. HTML placed before email content e.g. to remind subscribers what this
  publication is.
- `banner image URL`. An image placed at at the top of emails.
- `default author name`. Used in email content.
- `default author URL`. Used in email content.
- `default author image URL`. Used in email content.
- `filter tag`. Text. If set, posts are not automatically sent if they don't
  have this tag.
- `remove tag`. Text. If set, posts are not automatically sent if they have this
  tag.
- `archived at`. If set, the publication cannot be sent, subscribed to, or
  edited, except to unarchive it.

Details:

- We shouldn't prevent a user from having multiple publications for the same
  feed in case they want to send the same content to different audiences.

## Feed

An RSS feed, Atom feed, JSON feed, or any other kind of file we can fetch with a
GET request to get posts.

Required:

- `created at`
- `fetched at`. The latest time we attempted to fetch this feed, successfully or
  not.
- `url`
- `failed syncs`. The number of times we have attempted and failed to fetch this
  feed since the last successful fetch (or since the feed was added). Default 0.

Optional:

- `etag`. HTTP response header.
- `last modified`. HTTP response header.

## Post

An item from a feed.

Required:

- `feed ID`
- `fetched at`. The earliest time at which we first observed this post in the
  associated feed.
- `present as of`. A time at which we observed this post in the associated feed.
  This is not necessarily the most recent time we've fetched the feed and found
  this post there. This attribute is used to allow users to see posts currently
  in a feed they've added to their publication without being able to see posts
  that were previously in the feed but aren't currently. Thus it is only updated
  when a publication is created or updated.

Optional, taken mostly verbatim from the feed:

- `GUID`
- `published at`
- `title`
- `url`
- `content ID`. object storage foreign key. content is json with keys `html`
  and/or `text`.
- `content hash`. A hash of the object referenced by `content ID`.
- `tags`
- `author name`
- `author url`
- `author image`

Other optional attributes:

- `length`. # of characters in plain text version of the content, i.e. not
  including markup.
- `excerpt`. The first 500 characters of the plain text version of the content.

## Subscriber

A person who subscribes to a publication via the associated subscribe form. If a
they subscribe to multiple publications, there is a separate subscriber entity
for each publication.

Required:

- `email`
- `publication ID`
- `subscribed at`
- `require confirmation`. The value of the publication's `require confirmation`
  setting at the time of `subscribed at`. If the subscriber previously
  unsubscribed and is attempting to re-subscribe, this field is set to true
  regardless of the publication setting.

Optional:

- `confirmation triggered at`. The time at which a confirmation email was sent.
- `confirmation token` (blob). A randomly generated token used for confirmation.
- `confirmed at`. The time at which the subscriber successfully confirmed their
  subscription.
- `headers` (JSON blob). The HTTP request headers from when the subscribe form
  was submitted.
- `form params` (JSON blob). The form parameters from when the subscribe form
  was submitted.
- `query params` (JSON blob). The query parameters from when the subscribe form
  was submitted.
- `unsubscribed at`. The time at which the subscriber unsubscribed from this
  publication.
- `suppressed`. Set if the subscriber marks the publication's email as spam or
  if they hard bounce. Emails are not sent to this subscriber even if they
  re-submit the subscribe form.

## Send

Represents the sending of a post(s) to a publication.

Required:

- `publication ID`
- `started at`. The time at which this send was created.
- `progress at`. The last time progress was reported for this send. Default
  `started at`.
- `status` (enum: pending or finished).
- `from name`
- `subject`.
- `content ID`. Object storage foreign key. Content is json with keys `html` and
  `text`.
- `provenance` (enum: manual or automatic). Whether this send was triggered by
  the user manually or not.

## Send post

Marks which post(s) were used to generate the content for a send.

Required:

- `send ID`
- `post ID`

## Send attempt

Marks that an email send attempt is about to be made. Sends use "at most once"
semantics, so the presence of this entity does not mean the send was actually
made successfully.

Required:

- `send ID`
- `subscriber ID`

