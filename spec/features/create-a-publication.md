# Create a publication

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
supported for free users, except via admin imports. Users can change publication
settings after creating it:

- feed URL (we convert this to a feed ID transparently, fetching the feed and
  saving/updating posts using the same logic as when creating the publication)
- automatic sending
- require confirmation
- title, description, intro, optional archive URL and website URL
- reply-to email address (initially the owner's email address)
- form style, placeholder, and optional hidden form title
- email style
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

## Feed details

When the user enters a website or feed URL, we always use the final resolved URL
(e.g. after redirects).

We support RSS, Atom, and JSON feed.

## Archiving publications

When editing a publication, there is an "Archive" button which hides the
publication from the main publication list page. You can still navigate to a
list of archived publications. For archived publications:

- you cannot edit them, except that you can unarchive them.
- you cannot send manual emails to them.
- they are not eligible for automatic sends.
- the subscribe page/embedded subscribe form are disabled, as if the publication
  didn't exist.
- you cannot view the posts/subscribers/settings pages for them.
- feeds are not synced manually or automatically (except if those feeds are
  attached to other non-archived publications).
