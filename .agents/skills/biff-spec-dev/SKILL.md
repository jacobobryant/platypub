---
name: biff-spec-dev
description: Make the codebase conform to the spec.
---

There should be a `spec/agent/plan.md` file containing a checklist with
references to parts of the spec that need to be implemented. There may also be a
`spec/agent/questions.md` file with open questions and recommendations. If there
is not a `plan.md` file, output this text:

> The `spec/agent/plan.md` file must exist before running this skill. Run the
> `biff-spec-review` skill to generate it.

Replace "Run the biff-spec-review skill" with the exact text for running that
skill in the current agent harness, e.g. "Run `$biff-spec-review`" for Codex, "Run
`/biff-spec-review`" for Claude, etc.

Implement the plan, following the recommendations for any open questions. Mark
items off the checklist (replace `[ ]` with `[x]`) as you go. If additional open
questions come up during implementation, add them under a `## Questions during
implementation` section in `questions.md`.

After implementation, run the `biff-code-review auto` and `biff-code-quality`
skills. You do not need to tell the user the path of the review checklist file.
If the implementation is likely to be large (e.g. >500 lines of code), read
`rules.md` from the `biff-code-review` skill prior to implementation.

Verify all your work. If there are frontend changes, use a headless browser to
verify. If you need to create any files as part of that (e.g. for playwright),
put them in a temporary folder outside source control.

Prior to implementation, take a snapshot of the spec by copying `spec/` to a
temporary folder. If the user says "update", then take another snapshot and
compare it to the previous snapshot with `diff` etc, update `plan.md` and
`questions.md` accordingly, and implement the changes. See the
`biff-spec-review` skill for guidance for the content of `plan.md` and
`questions.md`.

If the user says "done", then delete `plan.md` and `questions.md`.
