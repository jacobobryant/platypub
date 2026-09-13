---
name: biff-code-quality
description: Ensure the code-quality task passes.
---

Run `clojure -M:run code-quality` and ensure it passes. Do not relax constraints
in the clj-kondo / cljfmt config like max line length; just fix the the code to
meet the current constraints.
