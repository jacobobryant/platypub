## Email providers

The only supported email provider is https://mailersend.com. Include an
environment config setting to specify what plan is being used, since some
features we want to use may only be available on certain plans (like the ability
to set unsubscribe headers).

### Complaint and bounce handling

Every 6 hours, a scheduled task should use the email provider's API to get a
list of email addresses who have complained or hard bounced in the past 24
hours. `suppressed` should be set for these email addresses on all publications.

These limitations are acceptable for now:

- Suppression doesn't apply to future subscriptions on other publications.
- Complaint/hard bounce events could be missed if polling is interrupted for
  over 24 hours.
