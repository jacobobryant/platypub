# Refactoring

These are areas that are known to be ugly/in need of refactoring:

- model.request: attribute parsing is ad-hoc; it would be better if it were more
  declarative/automatic similar to how reitit routes accepts a malli schema and
  will coerce parameters into the appropriate types.

- model.request: the attributes that request parameters get placed into are
  sometimes overly complicated/nested

- lib.middleware: app-access is gross in multiple ways. (1) does a
  non-primary-key-lookup query; (2) sometimes runs a sqlite transaction; (3)
  return value is inserted into ctx. Instead we should make :user/tier required
  and set it when users are created. request handlers that need to know the tier
  (e.g. is it admin or free) should get that info via biff.graph.

- use remus for parsing feeds

- currently using sqlite for object storage, which honestly is fine for now. But
  if/when this is deployed in non-waitlist mode, may want to switch to using
  actual object storage.

- biff.sqlite should autogen more resolvers, like lookup-by-unique-field(s) and
  back references.

- search params are modeled in the graph weirdly, e.g. `:subscriber/search`
  instead of, say, `:request/subscriber-search`

- revisit the custom fx handlers. biff should probably provide handlers for
  working with atoms. custom handlers should use `platypub.fx` for the namespace.

- need some integration/e2e tests. probably plenty of work to do on the tests in
  general; I haven't even looked at them.

- I think it'd be nice to have some kind of generated document/visualization of
  the entire data model including resolvers.

- there's some weird stuff in the authorization rules like the idea that a user
  can own a feed or a post. Maybe should make rules declarative/specified as
  data so it can be vizualized and/or more easily dictated as part of the spec.
  and/or maybe wouldn't be that hard to go ahead and just write the rules up as
  part of the spec without any additional tooling, similar to the data model.

- more structure for tab state. e.g. make the keys namespaced by current page?

- remove vestiges from the starter project.

- pull schema helper functions from schema.clj into a new
  com.biffweb.sqlite.schema namespace.

- maybe use #profile in config.edn (introduce config.prod.edn / config.dev.edn
  maybe via profile + merge + include)

- Apparently biff.graph doesn't actually work with `:type :edn` columns when
  they contain maps because it always uses scalar descriptors instead of `[:*]`.
  need to fix that and then change `:type :blob` to `:type :edn`.

- biff.graph: support :?foo/bar as an alias for [:? :foo/bar] maybe?

- don't use BIFF_PROFILE=prod in the e2e tests. add a new `test` profile if
  needed or something.

- biff.sqlite: support :default, then make :publication/address required with a
  default

- biff.sqlite: add execute-one probably (handy for resolvers)

- biff.datastar: make sure we're propagating :status appropriately

- revisit the way dialogs/modals work

- application code shouldn't use biff.auth config for subscriptions. it's ok for
  config.edn to use the same env var for both though, at least as a default

- prod-setup task used 8080 for caddy port but 8087 for systemd port

- prod-logs still doing that thing where it doesn't follow the latest logs
  properly

- biff.admin: fix problem with send-email not being in ctx when the listener
  starts
