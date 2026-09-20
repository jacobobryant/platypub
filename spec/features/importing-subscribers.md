# Importing subscribers

Admin users have a dashboard where they can search for a publication (owned by
any user) and then import subscribers for a selected publication. Publications
are not shown until the admin enters a search query.

To import subscribers, the admin uploads CSV containing an `email` column.
We skip emails for which the publication already has a subscriber (active or
not). For all the new imported subscribers, we set `require confirmation =
false`. Invalid rows are skipped.
