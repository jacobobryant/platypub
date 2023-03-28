# Platypub

A blogging + newsletter platform. Platypub is basically (1) a CMS, with (2) integrations for Netlify (for hosting your site) and Mailgun (for sending your newsletter) and (3) an extremely flexible theme system. Platypub can run as a multi-tenant managed service (e.g. as opposed to Ghost/Wordpress, where each user must have their own instance), or you can self-host it or even run it locally. It's meant to combine the convenience of e.g. Substack with the flexibility of a static site generator.

Platypub is not ready for general use yet. When it is, I'll host a public instance of it (with usage-based pricing and a free tier).

Platypub is built with [Biff](https://biffweb.com/), a Clojure web framework. If you're a Clojurist and you'd like to contribute, see [the roadmap](https://github.com/users/jacobobryant/projects/1/views/1) for a list of good first issues.

See also [Announcing Platypub: open-source blogging + newsletter tool](https://biffweb.com/p/announcing-platypub/).

<img width="800px" style="margin-right:10px" src="https://user-images.githubusercontent.com/3696602/194668648-c0950e8e-c595-404a-a0e2-d6c7847b43ce.png" />

<img width="800px" src="https://user-images.githubusercontent.com/3696602/194668694-c7b968ec-0900-4f1e-aa80-fafc0feed911.png" />

## Getting started

Prerequisites:
 - JDK 11 or higher
 - [clj](https://clojure.org/guides/getting_started)
 - [Babashka](https://github.com/babashka/babashka#quickstart) (`bb` should be on your path)
 - (Optional) API keys for Netlify, S3, Mailgun, and Recaptcha (see `config.edn` and `secrets.env`). You can run Platypub without these, but
 most of the features won't be available.

Run the following:

```
cp config.edn.TEMPLATE config.edn
cp secrets.env.TEMPLATE secrets.env
```

Run `bb generate-secrets` and paste the output into `secrets.env`. Also edit `config.edn` and `secrets.env` as needed.

Then you can start Platypub with `bb dev`. After you see a `System started` message, the app will be running on `localhost:8080`.
Once you've signed in, you can create a site, a newsletter,
and some posts as described in the [default theme setup](https://github.com/jacobobryant/platypub/tree/master/themes/default#setup).

## Deployment

You don't need to deploy Platypub to use it. You can run it locally, since any sites you create
will be hosted externally on Netlify anyway. However if you'd like to deploy it for convenience,
uncomment the `:com.platypub/allowed-users` and `:com.platypub/enable-email-sigin` config keys first.

See [the Biff docs](https://biffweb.com/docs/#production) for deployment instructions.
