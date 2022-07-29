# Platypub

An in-depth, real-world example project made with [Biff](https://biffweb.com/). Platypub is a publishing platform that's meant to give you the same amount of control as you would get from a static site generator,
while providing the same level of convenience as WordPress, Ghost, Substack etc. In addition to scratching my own itch, Platypub is
intended to help people learn Biff by providing a fun opportunity to hack on an open-source application. See the [list of good first issues](https://github.com/jacobobryant/platypub/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) if you'd like to start contributing.

See also [Announcing Platypub: open-source blogging + newsletter tool](https://biffweb.com/p/announcing-platypub/). Platypub is very early stage and needs a lot of work. Nevertheless I am already using it for both
[biffweb.com](https://biffweb.com) and [blog.thesample.ai](https://blog.thesample.ai/).

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

## Roadmap

Stage 1: Platypub can be used locally by a single user. (Current stage)

Stage 2: Platypub can be ran as a managed web service and used by multiple users, without
custom themes. Users must supply their own API keys for Mailgun, Netlify, and Recaptcha. And
maybe S3.

Stage 3: Like stage 2, but with custom themes. Need to be able to run themes in
a sandbox, ideally with https://www.cloudflare.com/lp/workers-for-platforms/.
I'll apply for access once we're ready, though I'm guessing they only care
about enterprise at this point. If needed we could hopefully use AWS Lambda
without much too much trouble, though I'm inclined to just wait until
Cloudflare opens up access. (Probably everyone who uses Platypub will be self-hosting/running it
locally for a while anyway.)

Stage 4: Like stage 3, but users don't have to bring their own API keys.

Stage 5: Theme development can be done from within Platypub. Platypub becomes
good at sucking people into Clojure/programming in general.

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
