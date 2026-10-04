# Subscribing

The publication page provides a hosted subscribe URL and an HTML embed snippet.
Both use the publication's settings. A visitor enters an email address and
submits the captcha-protected form. The hosted page uses the publication title
as its HTML page title. The embed snippet resizes its iframe as the form's
content changes, including after captcha and confirmation updates.

Email addresses are normalized by trimming whitespace from the ends and
converting to lower case.

- If the email address is not already associated with a subscriber entity for
  this publication, we create a subscriber entity and set `require confirmation`
  to the value of the publication's `require confirmation` attribute.
- If the subscriber entity already exists, `unsubscribed at` is set, and
  `suppressed` is not set, then clear the `unsubscribed at` and `confirmed at`
  attributes and set `require confirmation = true`. Also update `subscribed at`.
- Otherwise, do not update the database, except as described for confirmation
  emails.

In all cases, the subscribe form should show the same message afterward (even if
that message is inaccurate, like "we've sent a confirmation email") since we
don't want to expose any information about the subscriber's state.

## Confirmation emails

A confirmation email uses the publication title as its From and Reply-To name,
and the publication's reply-to email setting as its Reply-To address. Its
subject is "Confirm your subscription".

A confirmation email is sent when:

- someone successfully submits the signup form
- `suppressed` is not set
- `require confirmation` is true
- `confirmed at` is not set

When sending a confirmation email, a random token is stored in `confirmation
token` and `confirmation triggered at` is set to the current time. The
confirmation email includes a link with the token embedded in a path parameter
(email clients sometimes remove or alter query parameters). The token is valid
for 24 hours. If the user clicks the link and the token is still valid,
`confirmed at` is set to the current time, and `confirmation triggered at` and
`confirmation token` are both cleared.

## Welcome email

A welcome email uses the same From and Reply-To details. Its subject is
"Welcome".

A welcome email is sent when someone who was not previously an active subscriber
becomes an active subscriber, except when an admin imports subscribers. Imported
subscribers do not receive a welcome email.

## Unsubscribing

Send emails (i.e. not welcome or confirmation emails) always include an
unsubscribe link at the bottom. They also set whatever unsubscribe headers are
allowed by the email provider for the current plan. When a user unsubscribes,
`unsubscribed at` is set to the current time. The unsubscribe links/headers
contain a JWT that's valid for 30 days. A signing secret is provided as an
environment config setting. For GET requests, an unsubscribe confirmation page
is shown with the user's email address and a button to confirm. For POST
requests, the unsubscribe is processed immediately.

## Implementation

For captcha protection, default to cloudflare turnstile, falling back to
hcaptcha if the user is blocking cloudflare. There is a config option which can
be set to disable captcha; this flag will be set in dev.
