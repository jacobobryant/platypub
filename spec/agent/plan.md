## Add

1. [x] Show an error when no feed is discovered and let the user choose when a
   website advertises multiple feeds.
   [Publication setup](/spec/features/create-a-publication.md?lines=3-6)

2. [x] Display publication images and provide image uploads that record
   object-storage CDN URLs.
   [Publication setup](/spec/features/create-a-publication.md?lines=42-43)

3. [x] Show both the subscribe-form preview and a rendered Lorem Ipsum email
   while editing publication settings.
   [Publication setup](/spec/features/create-a-publication.md?lines=45-46)

4. [x] Add Turnstile protection with an hCaptcha fallback to hosted and embedded
   subscribe forms, including server-side verification. Honor the captcha
   disable flag and enable that bypass in development.
   [Subscribing](/spec/features/subscribing.md?lines=3-5)
   [Captcha setup](/spec/features/subscribing.md#implementation?lines=57-61)

5. [x] Add subscriber pagination controls and show an unsubscribe dropdown only
   for active subscribers.
   [Subscriber management](/spec/features/subscriber-management.md?lines=3-7)

6. [x] Print the text of every would-be outgoing email in development, including
   sign-in, confirmation, welcome, and newsletter emails.
   [Implementation](/spec/implementation.md?lines=11-13)

7. [x] Provide the specified :platypub.fx/swap! handler that looks up its atom
   in the system map and resolves qualified function symbols.
   [Implementation](/spec/implementation.md?lines=1-9)

8. [x] Add S3-compatible object storage using the MinIO Java SDK and biff.fx
   handlers, with a biff.core module that starts local MinIO when the
   development flag is enabled.
   [Object storage](/spec/implementation.md?lines=20-24)

9. [x] Add a mock CDN endpoint gated by the local-MinIO flag and a configurable
   CDN URL template. Enable local MinIO in config.env, set the mock URL in the
   development template, and document the DigitalOcean Spaces URL format without
   a production default.
   [Development storage and CDN](/spec/implementation.md?lines=20-29)

## Remove

10. [x] Remove the owner-facing re-subscribe action and change its server
    endpoint to unsubscribe active subscribers only, without reactivating
    inactive subscribers.
    [Subscriber management](/spec/features/subscriber-management.md?lines=3-7)

## Change

11. [x] Use Remus to parse feeds instead of the custom XML/JSON parser.
    [Implementation](/spec/features/feed-syncing.md#implementation?lines=49-51)

12. [x] Use actual signed JWTs for unsubscribe links with the configured signing
    secret and 30-day validity instead of the custom two-part EDN token format.
    [Unsubscribing](/spec/features/subscribing.md#unsubscribing?lines=48-55)

13. [x] Queue publication batches as send-readiness items and process each
    publication in the batch.
    [Send
    readiness](/spec/features/sending-emails.md#send-readiness-queue?lines=8-10)

14. [x] Use MailerSend personalization to substitute each recipient's
    unsubscribe URL into stored email templates.
    [Email rendering](/spec/features/email-rendering.md?lines=40-44)

15. [x] Move post and rendered-send content from SQLite to JSON objects in
    S3-compatible storage; update content references, feed writes, preview
    reads, send creation, and delivery reads; remove the SQLite content column.
    [Post content](/spec/data-model.md#post?lines=108-110)
    [Send content](/spec/data-model.md#send?lines=169-170)
    [Object storage](/spec/implementation.md?lines=20-24)

## Fix

16. [x] Reject feeds with no usable posts before creating a publication or
    changing its feed, including entries with empty content and no URL.
    [Publication setup](/spec/features/create-a-publication.md?lines=8-13)
    [Post content](/spec/features/feed-syncing.md#post-content?lines=29-33)

17. [x] Fetch a current feed body when creating a publication or changing its
    feed so metadata and present-as-of updates use only posts in that response;
    do not treat every historical post as currently present after a 304
    response.
    [Publication setup](/spec/features/create-a-publication.md?lines=8-25)

18. [x] Write the newly resolved feed ID when saving a feed change and preserve
    the submitted automatic-sending state when resetting its threshold. Reset
    the threshold when an enabled publication changes feeds.
    [Publication setup](/spec/features/create-a-publication.md?lines=31-33)
    [Send readiness
    ](/spec/features/sending-emails.md#send-readiness-queue?lines=27-29)

19. [x] Initialize the welcome-HTML textarea with the stored value so saving
    other settings does not erase it.
    [Publication setup](/spec/features/create-a-publication.md?lines=29-40)

20. [x] Record fetched-at and increment failed-syncs for unsuccessful fetches so
    the existing retry-delay schedule takes effect.
    [Feed data](/spec/data-model.md#feed?lines=74-79)
    [Feed syncing](/spec/features/feed-syncing.md?lines=3-13)

21. [x] Clear optional post fields when they disappear from a matching feed
    entry instead of retaining stale values.
    [Post matching](/spec/features/feed-syncing.md#matching-posts?lines=37-47)

22. [x] Preserve plain-text feed content as text and escape it when producing
    HTML; compute content hashes from the stored JSON object and derive length
    and excerpt from the correct plain text.
    [Post data](/spec/data-model.md#post?lines=102-120)

23. [x] Include posts previously sent by the publication even when they belong
    to an old feed.
    [Post visibility](/spec/features/viewing-posts.md?lines=5-10)

24. [x] Count and select subscribers with an unset suppressed value as eligible
    when their other active-subscriber conditions hold. Use that eligibility for
    delivery.
    [Active subscribers](/spec/glossary.md?lines=5-7)
    [Send processing](/spec/features/sending-emails.md?lines=38-40)

25. [x] Send a welcome email after confirmation only if the subscriber becomes
    active; do not send one if the subscriber is suppressed or remains
    unsubscribed.
    [Welcome emails](/spec/features/subscribing.md#welcome-email?lines=42-44)

26. [x] Set supported unsubscribe headers through MailerSend's email payload and
    account-plan capabilities instead of HTTP request headers. Honor the
    configured provider plan.
    [Unsubscribing](/spec/features/subscribing.md#unsubscribing?lines=48-52)
    [Email provider](/spec/features/email-providers.md?lines=3-6)

27. [x] Replace comma/line splitting with clojure.data.csv for subscriber
    imports, supporting quoted fields, escaped quotes, and embedded commas or
    newlines while retaining invalid-row and existing-subscriber skipping.
    [Subscriber imports](/spec/features/importing-subscribers.md?lines=7-10)
    [CSV parser](/spec/implementation.md?lines=31-31)

28. [x] Implement provider-aware throttling and rate-limit backoff/retry while
    preserving send-attempt records; log and skip other API failures.
    [Send processing](/spec/features/sending-emails.md?lines=42-55)

29. [x] Update progress-at with the time each delivery attempt is recorded
    rather than reusing the consumer's initial timestamp throughout a long send.
    [Send data](/spec/data-model.md#send?lines=163-165)
    [Send processing](/spec/features/sending-emails.md?lines=49-51)

30. [x] Render the manual-send preview as HTML using full publication settings,
    preserve its post data through confirmation, and deliver the stored From
    name instead of the publication's current title.
    [Manual sends](/spec/features/sending-emails.md#manual-sending?lines=65-74)

31. [x] Render author information for single-post emails as well as multi-post
    emails.
    [Email rendering](/spec/features/email-rendering.md?lines=16-36)

32. [x] Apply the publication's primary color to email links and buttons.
    [Publication data](/spec/data-model.md#publication?lines=31-38)

33. [x] Poll the actual MailerSend activity API with the sending domain,
    supported event filters and date parameters, read recipient addresses from
    its response structure, and follow pagination to suppress every matching
    address across publications.
    [Suppression polling](
    /spec/features/email-providers.md#complaint-and-bounce-handling?lines=10-12
    )

34. [x] Make user tier required and make the first-user check and account
    creation atomic so concurrent initial signups cannot create multiple admins.
    [User data](/spec/data-model.md#user?lines=9-13)
    [Signup](/spec/features/signup.md?lines=3-9)
    [Tier initialization](/spec/features/signup.md#implementation?lines=17-19)

35. [x] Resolve the source TODOs, including request/tab state for multipart
    uploads, optional graph attributes, feed synchronization edge cases,
    MailerSend bulk delivery, and worker lifecycle ownership.

36. [x] Resolve environment-specific config overlays while preserving the
    deploy task's referenced untracked-file list.

37. [x] Add Playwright coverage for the major signup, publication setup, feed
    sync, settings, subscription, subscriber administration, manual send, and
    unsubscribe flows, and make the full browser suite pass.

38. [x] Expand Playwright coverage so every user-observable requirement in the
    spec is exercised, with an explicit traceability map for requirements that
    are necessarily verified below the browser boundary.
    [MVP features](/spec/features.md)
    [Data model](/spec/data-model.md)
    [Implementation](/spec/implementation.md)
