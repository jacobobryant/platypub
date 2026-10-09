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

- search params are modeled in the graph weirdly, e.g. `:subscriber/search`
  instead of, say, `:request/subscriber-search`

- I think it'd be nice to have some kind of generated document/visualization of
  the entire data model including resolvers.

- there's some weird stuff in the authorization rules like the idea that a user
  can own a feed or a post. Maybe should make rules declarative/specified as
  data so it can be vizualized and/or more easily dictated as part of the spec.
  and/or maybe wouldn't be that hard to go ahead and just write the rules up as
  part of the spec without any additional tooling, similar to the data model.

- more structure for tab state. e.g. make the keys namespaced by current page?

- remove vestiges from the starter project.

- don't use BIFF_PROFILE=prod in the e2e tests. add a new `test` profile if
  needed or something.

- application code shouldn't use biff.auth config for subscriptions. it's ok for
  config.edn to use the same env var for both though, at least as a default

- make :publication/address required with a default
