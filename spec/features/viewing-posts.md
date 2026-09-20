# Viewing posts

After signing in, users see a list of their publications. After navigating to a
particular publication, they see a list of posts for that publication (the
"publication page"). Posts are shown if:

- they are part of a send for this publication, OR
    - they are part of the publication's current feed, AND
    - the post's `present as of` value is >= the publication's `feed ID updated
      at` value.

This spec guarantees that a publication always has at least one post since
you cannot set a publication's feed to a feed that doesn't have at least one
post.

Posts are sorted (first by `fetched at`, then by `published at`, then by ID) and
paginated (20 per page). The list view shows if and when a post was sent (the
send's `started at` value). There is no page to view an individual post.
