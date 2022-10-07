# Platypub default theme

Must have `bb` on path.

## Setup

Within Platypub, you'll want to have at least one site, newsletter, and post.

For using this theme as-is, you'll also want to create three special pages:

 - One with `welcome` for the tag. The contents of this post will
   be emailed to new subscribers.
 - One with `/subscribed/` for the path.
   This page will be rendered at `http://localhost:8888/subscribed/` and will
   be shown to new subscribers. It should say something like "check your inbox
   for a welcome email."
 - One with `/about` for the path. This will
   be linked to from the default site's nav bar.

## Theme development

See https://github.com/jacobobryant/platypub-theme-minimalist for an example of a custom
theme that reuses code from Platypub's default theme. To create your own custom theme,
you can copy that repo's contents into a `themes/my-custom-theme` directory, and then
make your changes.

### Website

#### Option 1: develop from within Platypub

Run Platypub, go to the Sites tab, click "preview." Edit theme files, save, and
refresh the page to make changes.

Note that the newsletter signup form will not work; to test that you'll need to
use option 2.

#### Option 2: develop with `netlify dev`

 1. Go to the Sites page on Platypub and click "export" to download an
 `input.edn` file. Copy it to this directory.
 2. Run `bb dev`

This will open a browser tab to `localhost:8888` with your site. Whenever you
save a file, the site will be regenerated. You'll need to hit refresh in the
browser tab manually.

If you want to update the site/newsletter/post data, you'll need to export the
`input.edn` file again.

### Email template

Run Platypub, make a post and open it, click "send", then click "preview."
Email clients are finnicky, so be sure to send a test email at least to GMail.
