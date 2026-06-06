(ns com.platypub.cli)

(defn watch
  "Builds your site whenever a file changes. Also starts a Netlify dev server."
  []
  ((requiring-resolve 'com.platypub.cli.watch/watch)))

(defn publish
  "Deploys your site to Netlify."
  []
  ((requiring-resolve 'com.platypub.cli.publish/publish)))

(defn test-email
  "Sends a test email to the given address.

   cljrun test-email [post path] [address]

   Example:

     cljrun test-email content/posts/some-post.md test@example.com"
  [path address]
  ((requiring-resolve 'com.platypub.cli.email/send-email) path address))

(defn publish-email
  "Publishes a post to your mailing list.

   cljrun publish-email [post path]

   Example:

     cljrun publish-email content/posts/some-post.md"
  [path]
  ((requiring-resolve 'com.platypub.cli.email/publish-email) path))

(defn subscribers
  "Prints your subscribers list.

   cljrun subs"
  []
  ((requiring-resolve 'com.platypub.cli.subscribers/subscribers)))

(defn clean-list
  "Unsubscribes people who have hard bounced or complained.

   cljrun clean-list"
  []
  ((requiring-resolve 'com.platypub.cli.subscribers/clean)))

(def tasks
  {"watch" #'watch
   "publish" #'publish
   "test-email" #'test-email
   "publish-email" #'publish-email
   "subs" #'subscribers
   "clean-list" #'clean-list})
