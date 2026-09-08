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
- `padding color`. Color to use for the space around email content and around
  the subscribe form. Default off-white.
- `background color`. Color to use for the backgrund of email content and the
  subscribe form. Default white.
- `text color`. Text color to use in emails and on the subscribe form. Default
  black.
- `primary color`. Color to use for links and buttons in emails and on the
  subscribe form. Default blue.
- `automatic sending enabled at`. When enabled, posts fetched after this time
  are sent automatically. Default `created at`.
- `welcome html`. Content for the welcome email. Defaults to "Thanks for
  subscribing."
- `require confirmation`. If true, new subscribers must confirm their
  subscription by clicking a link in an email ("double opt-in"). Default false.

Optional:

- `title`. Text used on the subscribe form and as the "From" name on emails.
- `description`. Text shown on the subscribe form.
- `intro`. Text placed before email text content e.g. to remind subscribers what
  this publication is.
- `banner image URL`. An image placed at at the top of emails and on the
  subscribe form.
- `default author name`. Placed in email content if the post author ID isn't
  set.
- `default author URL`. Placed in email content if the post author URL isn't
  set.
- `default author image URL`. Placed in email content if the post author image
  isn't set.
- `filter tag`. Text. If set, posts are not sent if they don't have this tag.
- `remove tag`. Text. If set, posts are not sent if they have this tag.

Details:

- We shouldn't prevent a user from having multiple publications for the same
  feed in case they want to send the same content to different audiences.

## Feed

An RSS feed, Atom feed, JSON feed, or any other kind of file we can fetch with a
GET request to get posts.

Required:

- `created at`
- `fetched at`
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

- `headers` (JSON blob). The HTTP request headers from when the subscribe form
  was submitted.
- `form params` (JSON blob). The form parameters from when the subscribe form
  was submitted.
- `query params` (JSON blob). The query parameters from when the subscribe form
  was submitted.
- `confirmed at`. The time at which the subscriber clicked the link in a
  confirmation email.
- `unsubscribed at`. The time at which the subscriber unsubscribed from this
  publication.
- `suppressed`. Set if the subscriber marks the publication's email as spam or
  if they hard bounce. Emails are not sent to this subscriber even if they
  re-submit the subscribe form.

## Send

Represents the sending of a post(s) to a publication.

Required:

- `publication ID`
- `started at`
- `last sent at`. The time at which the most recent send attempt was made.
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
