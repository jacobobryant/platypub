# Feed syncing

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
After a feed is fetched, all of its publications are placed on the send
readiness queue.

On the publication page, there is a "Sync feed" button that puts the
publication's feed on the queue immediately with a priority higher than the
feeds put on the queue by the scheduled task.

## Post content

When parsing a feed, if there is no content set but there is a URL, then the
content is `<a href="{URL}">URL</a>`. If there is no content and no URL, skip
the post. If the feed does not have any posts with URL or content set, the feed
does not count as having any posts and is invalid when trying to set a
publication's feed to it.

## Matching posts

When fetching feeds, a post in the feed matches a post entity we've already
created for that feed if:

- `GUID` is set and it matches, OR
- `GUID` is not set on the parsed feed, but `URL` is set and it matches, OR
- `GUID` and `URL` are not set on the parsed feed but `content hash` matches.

When a post matches, we update any fields that have changed instead of creating
a new post. We do not update `present as of` for existing posts; that attribute
is only updated in response to a publication being created/updated. We do set
`present as of` to `fetched at` when creating posts.

## Implementation

Use the Remus clojure library for parsing feeds.
