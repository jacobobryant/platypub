## Add

1. [x] Add publication archive state, archive and unarchive actions, an archived
   publications page, active-list filtering, and guards that prevent archived
   detail access, feed syncing, sends, editing, and subscriptions.
   [Archive state](/spec/data-model.md#publication?lines=46-64)
   [Archiving publications](
   /spec/features/create-a-publication.md#archiving-publications?lines=60-73)
   [Archived publications
   page](/spec/mockups/archived-publications.txt?lines=1-19)

2. [x] Store a required single-line address for each publication, expose it in
   settings, block sending while it is blank, and render it beside the
   unsubscribe link in HTML and text emails.
   [Publication address](/spec/data-model.md#publication?lines=21-44)
   [Address setting](/spec/mockups/publications/settings.txt?lines=20-22)
   [Email footer](/spec/mockups/email.txt?lines=17-47)

3. [x] Add the responsive application navbar, desktop sidebar, and mobile
   hamburger-controlled sidebar.
   [Navigation](/spec/mockups/nav.txt?lines=1-19)

4. [x] Show the feed's last-sync time on the posts tab and provide a Copy action
   for the embedded subscribe form.
   [Publication posts](/spec/mockups/publications/posts.txt?lines=10-26)

5. [x] Add reusable browser-timezone timestamp rendering and use the specified
   format for post-send and subscriber timestamps.
   [Timestamp styling](/spec/styling.md?lines=1-4)
   [Post timestamps](/spec/mockups/publications/posts.txt?lines=19-26)
   [Subscriber
   timestamps](/spec/mockups/publications/subscribers.txt?lines=11-17)

6. [x] Add Palette 7 as semantic Tailwind colors and apply the palette to the
   application's color scheme.
   [Color scheme](/spec/styling.md?lines=6-8)

## Remove

7. [x] Remove the public landing page and redirect signed-out root requests to
   sign-in while redirecting signed-in root requests into the application.
   [Signup](/spec/features/signup.md?lines=3-5)

## Change

8. [x] Change the publications page to use an Add publication modal, show only
   active publications, and show the archived-publications link only when an
   archive exists.
   [Publications page](/spec/mockups/publications.txt?lines=1-39)

9. [x] Give publication posts, subscribers, and settings pages the shared
   publication title, Publications breadcrumb, and active-tab navigation shown
   in the mockups.
   [Posts tab](/spec/mockups/publications/posts.txt?lines=3-7)
   [Subscribers tab](/spec/mockups/publications/subscribers.txt?lines=3-9)
   [Settings tab](/spec/mockups/publications/settings.txt?lines=4-14)

10. [x] Change settings previews into separate modals that render the current
   unsaved form values.
   [Settings previews](/spec/mockups/publications/settings.txt?lines=11-18)

11. [x] Change manual-send preview into a cancellable modal, re-render from
    current database values at confirmation, and redirect to the publication
    posts page after the send is confirmed.
    [Manual-send flow](/spec/mockups/publications/manual-send.txt?lines=19-34)

12. [x] Change the subscribe form heading and submitted state to match the
    mockup, including displaying the normalized submitted email address.
    [Subscribe form](/spec/mockups/publications/subscribe-form.txt?lines=1-15)
    [Indistinguishable response](/spec/features/subscribing.md?lines=19-21)

13. [x] Change subscriber row actions to an active-only dropdown whose
    Unsubscribe action is gated by a confirmation modal.
    [Subscriber
    actions](/spec/mockups/publications/subscribers.txt?lines=11-22)
    [Active-only
    action](/spec/features/subscriber-management.md?lines=3-7)

14. [x] Change email markup to use the specified single- and multi-post titles,
    post links, Read more links, published-at placement, and footer layout.
    [Email layout](/spec/mockups/email.txt?lines=1-47)

## Fix

15. [x] Fix development image uploads so saved publication images render from
    the configured mock CDN.
    [Image feedback](/spec/feedback.md?lines=3-4)

16. [x] Fix the Chassis fragment error in the manual-send rendering flow.
    [Manual-send feedback](/spec/feedback.md?lines=6-7)

## Staged updates

17. [x] Open the mobile sidebar with a native modal dialog and keep desktop
    navigation visible. [Navigation](/spec/mockups/nav.txt)

18. [x] Center standard dialogs, give text inputs white backgrounds, and
    improve interface text and control contrast.
    [Elements](/spec/mockups/elements.txt)

19. [x] Show temporary plain-text Copied feedback for the embedded form Copy
    action. [Posts](/spec/mockups/publications/posts.txt)

20. [x] Add a separate Archive publication section and redirect to the
    publications list after confirmation.
    [Settings](/spec/mockups/publications/settings.txt)

21. [x] Diagnose why Send appears disabled for a publication with a subscriber
    and make its prerequisites clear in the posts UI.
    [Feedback](/spec/feedback.md)
