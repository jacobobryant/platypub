# Platypub

A blogging + newsletter platform. Platypub is basically a CMS with integrations for Netlify (for hosting your site) and Mailgun (for sending your newsletter). Platypub supports multiple users (as opposed to wordpress or ghost where each user/publication must have its own instance), so you can run a public instance of it cheaply. Alternatively you can run your own instance of Platypub on your local machine--since hosting is done by Netlify, Platypub doesn't need to run all the time.

Platypub is not ready for general use yet. When it is, I'll host a public instance of it (with usage-based pricing and a free tier).

Platypub is built with [Biff](https://biffweb.com/), a Clojure web framework. If you're a Clojurist and you'd like to contribute, see [the roadmap](https://github.com/users/jacobobryant/projects/1/views/1) for a list of good first issues.

See also [Announcing Platypub: open-source blogging + newsletter tool](https://biffweb.com/p/announcing-platypub/).

<div>
<img width="450px" style="margin-right:10px" src="https://user-images.githubusercontent.com/3696602/194668648-c0950e8e-c595-404a-a0e2-d6c7847b43ce.png" />
<img width="450px" src="https://user-images.githubusercontent.com/3696602/194668694-c7b968ec-0900-4f1e-aa80-fafc0feed911.png" />
</div>

## Getting started

Prerequisites:
 - JDK 11 or higher
 - [clj](https://clojure.org/guides/getting_started)
 - [Babashka](https://github.com/babashka/babashka#quickstart) (`bb` should be on your path)
 - (Optional) API keys for Netlify, S3, Mailgun, and Recaptcha (see `config.edn` and `secrets.edn`). You can run Platypub without these, but
 most of the features won't be available.

Run the following:

```
cp config.edn.TEMPLATE config.edn
cp secrets.edn.TEMPLATE secrets.edn
cp config.sh.TEMPLATE config.sh
```
Then you can start Platypub with `./task dev`. After you see a `System started` message, the app will be running on `localhost:8080`.
Once you've signed in, you can create a site, a newsletter,
and some posts as described in the [default theme setup](https://github.com/jacobobryant/platypub/tree/master/themes/default#setup).

## Deployment

You don't need to deploy Platypub to use it. You can run it locally, since any sites you create
will be hosted externally on Netlify anyway. However if you'd like to deploy it for convenience,
uncomment the `:com.platypub/allowed-users` and `:com.platypub/enable-email-sigin` config keys first.

See [the Biff docs](https://biffweb.com/docs/#production) for deployment instructions.

## Commands

### `./task dev`

Starts the app locally. After running, wait for the `System started` message.
Connect your editor to nrepl port 7888. Whenever you save a file, Biff will:

 - Evaluate any changed Clojure files
 - Regenerate static HTML and CSS files
 - Run tests

### `./task format`

Format the code with cljfmt

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
