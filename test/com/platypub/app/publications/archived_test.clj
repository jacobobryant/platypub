(ns com.platypub.app.publications.archived-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.app.publications.archived :as archived]
            [tick.core :as tick]))

(deftest archived-page-state-test
  (let [[state]     (archived/archived-page)
        publication {:publication/id          (random-uuid)
                     :publication/title       "Archived"
                     :publication/archived-at (tick/instant
                                               "2026-09-27T00:00:00Z")}]
    (is (= 200
           (:status
            (state {} {:request/user
                       {:user/archived-publications [publication]}}))))))

(deftest archive-publication-state-test
  (let [[state]        (archived/archive-publication)
        publication-id (random-uuid)
        now            (tick/instant "2026-09-27T00:00:00Z")
        result         (state {:biff.fx/now now}
                              {:request/publication
                               {:publication/id publication-id}})]
    (is (= {:status 404} (state {} {})))
    (is (= :biff.sqlite.fx/authorized-write (get-in result [:_write 0])))
    (is (= now (get-in result [:_write 1 :set :publication/archived-at])))
    (is (= {:status 204} (:biff.fx/return result)))))

(deftest unarchive-publication-state-test
  (let [[state]        (archived/unarchive-publication)
        publication-id (random-uuid)
        result         (state {} {:request/archived-publication
                                  {:publication/id publication-id}})]
    (is (= {:status 404} (state {} {})))
    (is (= :biff.sqlite.fx/authorized-write (get-in result [:_write 0])))
    (is (nil? (get-in result [:_write 1 :set :publication/archived-at])))
    (is (= {:status 204} (:biff.fx/return result)))))
