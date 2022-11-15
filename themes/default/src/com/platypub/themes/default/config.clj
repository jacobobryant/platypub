(ns com.platypub.themes.default.config
  (:require [clojure.java.io :as io]))

(def config
  {:render-site ["bb" "run" "render-site"]
   :render-email ["bb" "run" "render-email"]
   :fields {:com.platypub.site/description    {:label "Description"}
            :com.platypub.site/image          {:label "Image"
                                               :type :image}
            :com.platypub.site/redirects      {:label "Redirects"
                                               :type :textarea}
            :com.platypub.site/primary-color  {:label "Primary color"
                                               :default "#181917"}
            :com.platypub.site/accent-color   {:label "Accent color"
                                               :default "#d97706"}
            :com.platypub.site/tertiary-color {:label "Tertiary color"
                                               :default "#f3f4f6"}
            :com.platypub.site/link-color     {:label "Link color"}
            :com.platypub.site/bg-color       {:label "Background color"}
            :com.platypub.site/logo-image     {:label "Navbar logo image URL"
                                               :description "Suggested dimensions: 320x60 px"
                                               :type :image}
            :com.platypub.site/logo-url       {:label "Navbar logo destination URL"
                                               :description "Where should people go when they click on the logo?"
                                               :default "/"}
            :com.platypub.site/home-logo      {:label "Home logo image URL"
                                               :description "Optional. A larger logo to display on the home page."
                                               :type :image}
            :com.platypub.site/author-name    {:label "Author name"}
            :com.platypub.site/author-url     {:label "Author URL"}
            :com.platypub.site/author-image   {:label "Author image URL"
                                               :type :image}
            :com.platypub.site/comments-url   {:label "Comments URL"
                                               :description "Optional. Adds a \"View comments\" button to your emails. Takes precedence over `Discourse forum URL`."}
            :com.platypub.site/discourse-url  {:label "Discourse forum URL"
                                               :description "Optional. If you set up a Discourse forum, you can use it to embed comments on your blog posts."}
            :com.platypub.site/embed-html     {:label "Embed HTML"
                                               :description "This snippet will be injected into the head of every page. Useful for analytics."
                                               :type :textarea}
            :com.platypub.site/nav-links      {:label "Navigation links"
                                               :default "/ Home\n/archive/ Archive\n/about/ About"
                                               :type :textarea}
            :com.platypub.post/title          {:label "Title"}
            :com.platypub.post/slug           {:label "Slug"
                                               :default [:slugify :com.platypub.post/title]}
            :com.platypub.post/draft          {:label "Draft"
                                               :type :boolean
                                               :default true}
            :com.platypub.post/published-at   {:label "Published"
                                               :type :instant}
            :com.platypub.post/tags           {:label "Tags"
                                               :type :tags}
            :com.platypub.post/description    {:label "Description"
                                               :type :textarea}
            :com.platypub.post/image          {:label "Image"
                                               :type :image}
            :com.platypub.post/canonical      {:label "Canonical URL"}
            :com.platypub.post/comments-url   {:label "Comments URL"
                                               :description "Optional. Adds a post-specific \"View comments\" button to your emails."}
            :com.platypub.post/html           {:type :html}

            :com.platypub.page/path           {:label "Path"
                                               :default [:slugify :com.platypub.post/title]}}
   :site-fields [:com.platypub.site/description
                 :com.platypub.site/image
                 :com.platypub.site/redirects
                 :com.platypub.site/primary-color
                 :com.platypub.site/accent-color
                 :com.platypub.site/tertiary-color
                 :com.platypub.site/link-color
                 :com.platypub.site/bg-color
                 :com.platypub.site/logo-image
                 :com.platypub.site/logo-url
                 :com.platypub.site/home-logo
                 :com.platypub.site/author-name
                 :com.platypub.site/author-url
                 :com.platypub.site/author-image
                 :com.platypub.site/comments-url
                 :com.platypub.site/discourse-url
                 :com.platypub.site/embed-html
                 :com.platypub.site/nav-links]
   :items [{:key :posts
            :label "Post"
            :slug "posts"
            :query [:com.platypub.post/slug]
            :fields [:com.platypub.post/title
                     :com.platypub.post/slug
                     :com.platypub.post/draft
                     :com.platypub.post/published-at
                     :com.platypub.post/tags
                     :com.platypub.post/description
                     :com.platypub.post/image
                     :com.platypub.post/canonical
                     :com.platypub.post/comments-url
                     :com.platypub.post/html]
            :sendable true
            :render/label :com.platypub.post/title
            :render/sections [{:label "Drafts"
                               :match [[:com.platypub.post/draft true]]
                               :order-by [[:com.platypub.post/title :desc]]
                               :show [:com.platypub.post/tags]}
                              {:label "Published"
                               :match [:not [[:com.platypub.post/draft true]]]
                               :order-by [[:com.platypub.post/published-at :desc]]
                               :show [:com.platypub.post/published-at
                                      :com.platypub.post/tags]}]}
           {:key :pages
            :label "Page"
            :slug "pages"
            :query [:com.platypub.page/path]
            :fields [:com.platypub.post/title
                     :com.platypub.page/path
                     :com.platypub.post/draft
                     :com.platypub.post/tags
                     :com.platypub.post/description
                     :com.platypub.post/image
                     :com.platypub.post/html]
            :render/label :com.platypub.post/title
            :render/sections [{:order-by [[:com.platypub.page/path :asc]]
                               :show [:com.platypub.page/path]}]}]})

(defn -main []
  (prn config))
