# Biff Starter Project

This is a starter project for [Biff](https://github.com/jacobobryant/biff).
Requirements:

- Java 17 or higher
- [Clojure](https://clojure.org/guides/install_clojure)

Create a new project:

```clojure
git clone https://github.com/jacobobryant/biff-starter my-project
cd my-project
clj -M:run dev
```

The first time you run the `dev` command you'll be prompted to choose a
namespace for the new project. After the app starts, go to `localhost:8080` and
sign in. The sign-in code will be printed to the console. Changes are evaluated
whenever you save a file.

Use `clj -M:run -h` to see the available commands.

## Project layout

- `resources/config.edn`: this file is checked into source and includes
  hard-coded config as well as environment-backed config.
- `config.env` and `config.prod.env`: these files are not checked into source
  and define the `#biff/env` / `#biff/secret` values referenced in
  `resources/config.edn`.
- `src/com/example/schema.clj`: SQLite database schema. This is used to
  autogenerate `resources/schema.sql`.
- `src/com/example.clj`: the entrypoint for your application. Typically you do
  not edit this file.
- `src/comp/example/app`: biff.core modules related to the user-visible
  functionality of your app. Mostly Reitit routes (frontend and backend).
- `src/com/example/model`: biff.core modules related to the application's data
  model. Mostly biff.graph resolvers.
- `src/com/example/modules.clj`: a vector biff.core modules, collected from the
  `app` and `model` folders.
- `src/com/example/lib`: shared functions.
- `src/com/example/routes.clj`: shared Reitit route paths.
- `repl.clj`: scratch space / development-time REPL code.

Files that contain biff.core modules should only be placed in folders dedicated
for modules (`app`, `model`, and any other module folders you decide to add).
Module namespaces should only be required by `modules.clj`. Module namespaces
should **never** require each other. Shared code should go in `lib`.

Some potential module folders you could add:

- `work`, for biff.background jobs and scheduled tasks.
- `api`, for external API routes.
- `ui_components`, for biff.graph resolvers that return hiccup.
