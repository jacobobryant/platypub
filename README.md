# Platypub (work in progress, see TODO)

See [Platypub: plans for building a blogging platform with Biff](https://biffweb.com/p/platypub-plans/).

In a nutshell, Platypub is an attempt to take the experience of writing your
own static site generator in Clojure, and factor out all the incidental parts
into a web service.

## Getting started

Run the following:

```
cp config.edn.TEMPLATE config.edn
cp config.sh.TEMPLATE config.sh
cp themes.TEMPLATE themes
npm install
./task install-tailwind
```

You'll also need `bb` and `tailwindcss` on your path. See [Babashka
Quickstart](https://github.com/babashka/babashka#quickstart). For Tailwind,
after running `./task install-tailwind` above, you could do `sudo cp
bin/tailwindcss /usr/local/bin/`.


Then add credentials for Netlify, S3, and Mailgun to your config. See the
comments in `config.edn`.

Finally, run `./task dev` to start the application

(If you get an error about `parse-uuid` not being defined, it means you need to
upgrade to Clojure 1.11.1. However I _think_ this shouldn't be a problem
because I added `org.clojure/clojure {:mvn/version "1.11.1"}` to `deps.edn`.)

After signing in, you can create blog posts and deploy/preview the site. There is a
default theme at `com.platypub.themes.default` which is currently empty. You can edit that
file, save, then click `Preview`, and your changes will be shown immediately.

You can create a new site and deploy it to a subdomain from Netlify. You can
add a custom domain to your site from within Netlify.

## TODO

Core functionality:

 - Add support for newsletters via Mailgun
 - Create a fully-featured default theme
 - Update the data model to support multiple users
 - Figure out some kind of sandbox for themes so that a managed instance of
   Platypub can safely run custom themes from users. Hopefully this is
   workable. If in-JVM sandboxing doesn't work, maybe run an external process
   (e.g. Babashka) and sandbox that somehow? Use Docker if we absolutely have
   to? Decide how themes should be packaged and shared.

Additional stuff:

 - Lots of various UI improvements, for example:
   - Make TinyMCE's color scheme match Platypub's in dark mode
   - Add tooltips etc as needed
   - Make `com.platypub.ui/image` support uploading images (currently I upload
     images in TinyMCE then copy the URL)
 - Make a simple landing page
 - (Bonus points) add some kind of editor within Platypub so you can edit
   themes from there
    - great way to get people hooked on Clojure
 - Schedule newsletters and/or posts
 - Import posts from a CSV or something
 - Integrate with Stripe so that anyone who wants to can provide Platypub as a
   managed service and at a minimum cover hosting costs
    - And if someone wants to try to build a business off of Platypub that
      would be awesome
 - And of course, documentation

## Commands

### `./task dev`

Starts the app locally. After running, wait for the `System started` message.
Connect your editor to nrepl port 7888. Whenever you save a file, Biff will:

 - Evaluate any changed Clojure files
 - Regenerate static HTML and CSS files
 - Run tests

### `./task clean`

Deletes generated files.

### `./task deploy`

`rsync`s config files to the server, deploys code via `git push`, and restarts
the app process on the server (via git push hook). You must set up a server
first. See [Production](https://biffweb.com/docs/#production).

### `./task soft-deploy`

`rsync`s config and code to the server, then `eval`s any changed files and
regenerates HTML and CSS files. Does not refresh or restart.

### `./task refresh`

Reloads code and restarts the system via `clojure.tools.namespace.repl/refresh`
(on the server). To do this in local development, evaluate
`(com.biffweb/refresh)` with your editor.

### `./task restart`

Restarts the app process via `systemctl restart app` (on the server).

### `./task logs`

Tail the server's application logs.

### `./task prod-repl`

Open an SSH tunnel so you can connect to the server via nREPL.

### `./task prod-dev`

Runs `./task logs` and `./task prod-repl`. In addition, whenever you save a
file, it will be copied to the server (via rsync) and evaluated, after which
HTML and CSS will be regenerated.
