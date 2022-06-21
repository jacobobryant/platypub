# Platypub

See [Announcing Platypub: open-source blogging + newsletter tool](https://biffweb.com/p/announcing-platypub/).

In a nutshell, Platypub is an attempt to take the experience of writing your
own static site generator in Clojure and factor out all the incidental parts
(CMS, deployment, mailing lists) into a web service.

Platypub is very early stage and needs a lot of work. Nevertheless I am already using it for both
[biffweb.com](https://biffweb.com) and [blog.thesample.ai](https://blog.thesample.ai/).

## Getting started

Run the following:

```
cp config.edn.TEMPLATE config.edn
cp config.sh.TEMPLATE config.sh
npm install
(cd themes/default; npm install)
```

You'll also need `bb` on your path. See [Babashka
Quickstart](https://github.com/babashka/babashka#quickstart). Then add
credentials for Netlify, S3, Mailgun, and Recaptcha to your config. See the
comments in `config.edn`.

Finally, run `./task dev` to start the application. Once you've signed in, you can create a site, a newsletter,
and some posts as described in the [default theme setup](https://github.com/jacobobryant/platypub/tree/master/themes/default#setup).

Some tasks must be done from within Netlify/Mailgun instead of Platypub. e.g.
you'll need to go to Mailgun to see your current subscriber list, and you'll
need to go to Netlify to set a custom domain.

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

## Commands

### `./task dev`

Starts the app locally. After running, wait for the `System started` message.
Connect your editor to nrepl port 7888. Whenever you save a file, Biff will:

 - Evaluate any changed Clojure files
 - Regenerate static HTML and CSS files
 - Run tests

### `./task clean`

Deletes generated files.

<!--
Uncomment this after we get to stage 2.

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
-->
