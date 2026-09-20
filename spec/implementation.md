Use a `:platypub.fx/swap!` biff.fx handler for swapping on atoms, for example:

```
[:platypub.fx/swap! :platypub/my-atom `update :foo `inc]
```

The first keyword is used to get an atom from the system map. qualified symbols
in the vector are resolved to functions. Then unit tests for the biff.fx machine
state can still show what the function does as data.

In development, emails that would be sent in production should be printed to the
console. Their text content is printed, not their html content. This includes
signin emails.

Use https://github.com/igrishaev/remus for parsing feeds.

No two sends for the same publication may share a post ID. Sends must always be
created in a transaction that enforces this invariant.

Use S3 compatible object storage via the minio java sdk. add a biff.core module
that starts up minio locally when a configuration flag is set. Set it in dev
(config.env). In prod we should be able to use any S3-compatible service such as
digitalocean spaces, real S3, etc. Add biff.fx handlers for interacting with
object storage. There should be a mock CDN api endpoint that's enabled in dev by
the same config flag. That mock endpoint returns objects from local minio. There
should be another config setting that has a template string used to get a CDN
URL. The dev template .env file should have a value for the mock CDN endpoint.
The prod template .env file shouldn't have a default setting, but in a comment
it should say what value you should use for digitalocean spaces.

Use clojure.data.csv for parsing CSVs.
