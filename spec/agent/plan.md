## Add

1. [x] Show an in-progress indicator while a publication is being created.
   [Publication creation](/spec/mockups/publications.txt?lines=41-42)

2. [x] Add separate one-post and multi-post email preview actions.
   [Preview actions](/spec/mockups/publications/settings.txt?lines=13-27)

## Remove

3. [x] Hide the subscribe page title after the form is submitted.
   [Submitted form](/spec/mockups/publications/subscribe-form.txt?lines=10-17)

## Change

4. [x] Render settings previews with the live form and email rendering code,
   using current unsaved form values. Disable the preview form's email input
   and omit its captcha widget.
   [Settings previews](/spec/mockups/publications/settings.txt?lines=29-35)

5. [x] Render the publication intro as HTML in send emails and settings
   previews.
   [Publication intro](/spec/data-model.md#publication?lines=52-53)

6. [x] Apply DOMPurify to settings and manual-send previews before rendering
   them in the browser.
   [Settings previews](/spec/mockups/publications/settings.txt?lines=29-37)
   [Manual-send preview](/spec/mockups/publications/manual-send.txt?lines=36-36)

7. [x] Remove the banner image from the hosted subscribe form and its preview.
   [Banner image](/spec/data-model.md#publication?lines=54)
   [Subscribe form](/spec/mockups/publications/subscribe-form.txt?lines=1-7)

8. [x] Place the subscribe form's captcha widget outside the email and button
   row, as shown in the mockup.
   [Subscribe form](/spec/mockups/publications/subscribe-form.txt?lines=4-7)

## Fix

9. [x] Give settings color inputs enough height to show their colors on
   tablet screens, and improve image upload control padding and background.
   [Settings feedback](/spec/feedback.md?lines=1-3)

10. [x] Place Archive publication below the settings form on large screens.
   [Settings feedback](/spec/feedback.md?lines=3-4)
