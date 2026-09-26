1. Which direction and null ordering should the publication's post list use?
   Recommendation: retain the existing fetched-at descending, published-at
   descending, and ID descending order, with unset publication dates last;
   document it separately from the ascending ordering inside emails.
   [Post-list ordering](/spec/features/viewing-posts.md?lines=23-25)
   [Email ordering](/spec/features/email-rendering.md?lines=3-6)

2. What should remain frozen after a manual-send preview if publication settings
   change before confirmation or delivery? Recommendation: freeze the rendered
   HTML, text, subject, From name, and Reply-To identity used by the preview for
   the entire send, while continuing to check subscriber eligibility at delivery
   time.
   [Preview
   consistency](/spec/features/sending-emails.md#manual-sending?lines=69-74)
   [Sender and reply-to rules](/spec/features/email-rendering.md?lines=8-14)
