# Questions for review

The implementation proceeds with these assumptions; none blocks the MVP:

- Which MailerSend plan names should enable RFC 8058 one-click unsubscribe
  headers? The implementation currently enables them for `professional` and
  always includes an unsubscribe link in the message body.
- Which CDN/object-storage service should production image uploads use? There
  is no object-storage service configured in the starter repository, so the
  settings screen currently accepts a CDN image URL directly. Feed and rendered
  email content is stored in the local Biff/SQLite content store.
- Should a final remaining admin be allowed to demote themself? The spec permits
  admins to edit themselves, so this is currently allowed.
