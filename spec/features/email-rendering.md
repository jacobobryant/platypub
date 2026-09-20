# Email rendering

Posts in an email are ordered first by if they have a URL or not (posts with
URLs come first), then by `fetched at` (oldest first), then by `published at`
(oldest first, unset last), then by post ID (ordering doesn't matter as long as
its stable).

The From address is hardcoded in the email provider settings (on their website)
or via an environment config setting if we need to include a value in our API
calls. The From name and Reply-To name are the publication title. The Reply-To
address is the user's `email`. The subject is the title of the first post that
has a title. If no posts have a title, the subject is the first 40 characters of
the first post's plain text content, with an ellipsis if the content is longer
than 40 characters.

Each post is rendered with its title, URL, author information, and
content/excerpt. If there is only one post, then we render the post's full
content. If there are multiple posts, we render the `excerpt` instead of the
full content and we also render `published at` if set. However, if a post has no
URL, we always render the full content, not the `excerpt`.

If there is only one post and it has a URL, the URL is rendered as a "Read
online" link. If there are multiple posts, each post's URL is rendered as the
link target for `published at` if set and as a "Read online" link if not.

If a post doesn't have an `author name` and the publication does have a `default
author name`, the post uses the `default author name`, `default author URL`, and
`default author image URL` from the publication as its author information. The
defaults are all-or-nothing; the publication's `default author name` would not
be used with the post's `author URL` for example. If the post doesn't have an
author name (whether from the post or the publication), the other author
information is ignored.

If there are multiple posts, author information is rendered along with each
post. Except that if each post has the same author information, the author
information is rendered once at the top of the email.

The intro is rendered in italics if set.

When creating the send and rendering the content to be stored, the unsubscribe
URL is rendered as a template value that will be replaced.
If the email provider has a way to provide email content as a template and
supply per-subscriber values to be inserted, do that. Otherwise, we should
supply separate content for each subscriber with the unsubscribe URL rendered.
