- subscribe form gives a 200 for the POST request but subscriber doesn't end up
  on list. maybe a captcha failure. logs should say why it failed.

- pub settings form froze on me. reproducible by opening a modal, switching to a
  different app (this is on mobile), then going back. Maybe would be fixed by
  having modal open state driven by a signal?

---

- need to test if cloudflare -> hcaptcha fallback works.

- add a pub setting to allow always using hcaptcha instead of cloudflare.

- app becomes unresponsive sometimes, pages won't even load. not sure if it's a
  problem with platypub or with another app on the host hogging resources.
  though restarting fixes it, so probably a platypub problem.

- subscribe form still shows "subscribe to <publication>" on the confirmation
  step
