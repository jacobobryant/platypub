- biff.sqlite should autogen more resolvers, like lookup-by-unique-field(s) and
  back references.

- revisit the custom fx handlers. biff should probably provide handlers for
  working with atoms. custom handlers should use `platypub.fx` for the namespace.

- pull schema helper functions from schema.clj into a new
  com.biffweb.sqlite.schema namespace.

- Apparently biff.graph doesn't actually work with `:type :edn` columns when
  they contain maps because it always uses scalar descriptors instead of `[:*]`.
  need to fix that and then change `:type :blob` to `:type :edn`.

- biff.graph: support :?foo/bar as an alias for [:? :foo/bar] maybe?

- biff.sqlite: support :default, then make :publication/address required with a
  default

- biff.sqlite: add execute-one probably (handy for resolvers)

- biff.datastar: make sure we're propagating :status appropriately

- prod-setup task used 8080 for caddy port but 8087 for systemd port

- prod-logs still doing that thing where it doesn't follow the latest logs
  properly

- biff.admin: fix problem with send-email not being in ctx when the listener
  starts

- biff.admin: show stuff like # of open sse connections
