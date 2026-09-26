# Spec coverage

This map keeps the executable coverage in `platypub.spec.js` tied to every
user-observable requirement in `spec/`. Requirements that have no browser
surface are listed with the lower-level test that verifies them; duplicating
those through a test-only HTTP endpoint would test the endpoint rather than the
production behavior.

| Spec | Playwright coverage |
| --- | --- |
| Signup | `the first user becomes an admin and can admit a waitlisted user` covers first-admin creation, waitlist access, tier changes, the irreversible waitlist transition, and admin authorization. |
| Create a publication | `publication setup rejects unusable feeds...`, `an owner handles feed discovery...`, and `an owner syncs...` cover failed and ambiguous discovery, redirects, RSS/Atom/JSON, unusable posts, reusable feeds, multiple publications, metadata defaults, immediate fetches, every editable field, previews, both image uploads, and persisted CDN URLs. |
| Importing subscribers | `an admin imports subscribers...` covers search-before-results, admin import, quoted fields, escaped quotes, embedded commas/newlines, invalid and duplicate rows, pagination, and the absence of welcome mail. |
| Viewing posts | `publication posts are ordered...` covers ordering, the 20-item page size, pagination, sent state, and the absence of post detail links. `an owner previews...` covers sent posts remaining visible after a feed change. |
| Subscribing | `a reader confirms...`, `a no-confirmation subscription...`, and `an owner previews...` cover the hosted and embedded form, normalized addresses, disabled development captcha, confirmation, immediate activation, indistinguishable duplicate responses, welcome mail, unsubscribe confirmation, JWT links, and confirmation-required resubscription. |
| Sending emails | `multi-post email rendering...` and `an owner previews...` cover send availability, post selection, preview, immutable preview data, manual priority input, active-recipient filtering, send attempts through observable delivery, finished sends, sent-post exclusion, From/Reply-To/subject, unsubscribe personalization, and headers. |
| Feed syncing | `an owner syncs...` covers priority/manual sync and newly fetched posts. Creation and feed-change tests cover current-response visibility and immediate synchronization. |
| Subscriber management | `an admin imports subscribers...` covers columns, search, 50-item pagination, active-only unsubscribe controls, and owner unsubscribe. |
| Email providers | The newsletter tests inspect the complete MailerSend mock payload, including personalization and plan-dependent unsubscribe fields. |
| Email rendering | The single- and multi-post send tests cover ordering, excerpts versus full content, URL links, publication/post authors, intro, colors, banner, sender identity, subject, and unsubscribe templates. |

## Non-browser boundaries

- Scheduled feed selection, conditional headers, retry delays, matching, stale
  field removal, plain-text storage, and Remus parsing are covered by
  `lib/feed_test.clj`, `work/feeds_test.clj`, and `work/feed_sync_test.clj`.
- Automatic readiness, tag matching, 24-hour gating, subscriber eligibility,
  queue ordering, throttling, rate-limit retries, progress updates, resumption,
  at-most-once attempts, and send completion are covered by
  `work/readiness_consumer_test.clj`, `work/send_test.clj`, and
  `work/resume_sends_test.clj`.
- Bounce/complaint polling and pagination are covered by
  `work/suppress_bounces_test.clj` and `api/mock_mailersend_test.clj`.
- JWT expiry/signatures, confirmation expiry, data-model constraints,
  transaction ownership, duplicate-post prevention, object-storage effects,
  the mock CDN, and `:platypub.fx/swap!` are covered by `lib/tokens_test.clj`,
  the app subscription tests, `schema_test.clj`, `fx_test.clj`, and the MinIO/CDN
  tests.
- Development email logging and environment overlays are configuration/startup
  concerns and are checked by the code-quality task rather than exposed through
  production HTTP routes.
