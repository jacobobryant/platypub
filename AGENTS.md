After you edit Clojure source files, run `trench -p $(cat .nrepl-port) -e
"((requiring-resolve 'com.biffweb.tasks/agent-refresh))"` to evaluate the
changes and run linting/tests. If `trench` is not already available, you can
download it from https://github.com/athos/trenchman/releases/latest. If the
application is not yet running, start it with `clojure -M:run dev` and keep it
running until you're done developing. When you've finished making changes, run
`clojure -M:run code-quality` and ensure it passes. If you started the app with
`clojure -M:run dev`, close it after you're done.
