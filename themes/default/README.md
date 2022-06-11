# Platypub default theme

Must have `bb` on path.

## Setup

Within Platypub, you'll want to have at least one site, newsletter, and post.
Tags are used to specify which sites, newsletters, and posts go together. For
experimenting, you could create one of each and assign them all a tag of
`example-site`.

For using this theme as-is, you'll also want to create three additional special posts:

 - One with `example-site welcome` for the tags. The contents of this post will
   be emailed to new subscribers.
 - One with `example-site page` for the tags and `subscribed` for the slug.
   This page will be rendered at `http://localhost:8888/subscribed/` and will
   be shown to new subscribers. It should say something like "check your inbox
   for a welcome email."
 - One with `example-site page` for the tags and `about` for the slug. This will
   be linked to from the default site's nav bar.

## Theme development

Make your own theme by copying this folder, e.g. `cp -r themes/default
themes/mytheme`. Then go to your site and newsletter in Platypub and change the
theme to `mytheme` (or whatever you called the directory).

### Website

#### Option 1: develop from within Platypub

Run Platypub, go to the Sites tab, click "preview." Edit `render-site` to make
changes.

Note that the newsletter signup form will not work; to test that you'll need to
use option 2.

#### Option 2: develop with `netlify dev`

 - Go to the Sites page on Platypub and click "export" to download an
   `input.edn` file. Copy it to this directory.
 - Run `npm install`
 - Run `./task dev`

This will open a browser tab to `localhost:8888` with your site. Whenever you
make a change to `render-site` and save the file, the site will be regenerated.
You'll need to hit refresh in the browser tab manually.

If you want to update the site/newsletter/post data, you'll need to export the
`input.edn` file again.

### Email template

Run Platypub, make a post and open it, click "send", then click "preview."
