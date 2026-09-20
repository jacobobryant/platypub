# Signup

The first user to sign up is put on the "admin" tier. The admin has access to a
dashboard that lets them edit the tier of other users (and themselves). After a
user has been moved from the waitlist to another tier, they cannot be moved back
on to the waitlist.

An environment config setting controls whether the app has a waitlist. If set,
new users (other than the admin user) are put on the "waitlist" tier. Waitlist
users are not able to use the app. When they sign in, they see only a message
saying that they're on the waitlist and will be notified when they are given
access to the application.

## Implementation

A user's tier should be set when that user is created.
