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

   cljrun email [post path] [address]

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

(def tasks
  {"watch" #'watch
   "publish" #'publish
   "test-email" #'test-email
   "publish-email" #'publish-email})
