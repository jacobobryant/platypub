- visualize resolvers somehow

- pull schema helper functions from schema.clj into a new
  com.biffweb.sqlite.schema namespace.

- biff.datastar: make sure we're propagating :status appropriately

- prod-setup task used 8080 for caddy port but 8087 for systemd port

- prod-logs still doing that thing where it doesn't follow the latest logs
  properly

- biff.admin: fix problem with send-email not being in ctx when the listener
  starts

- revisit the custom fx handlers. biff should probably provide handlers for
  working with atoms. custom handlers should use `platypub.fx` for the namespace.

- guidance/structure for model.request?

- biff.admin: show stuff like # of open sse connections
