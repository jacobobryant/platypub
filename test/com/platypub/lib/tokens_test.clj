(ns com.platypub.lib.tokens-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [com.platypub.lib.tokens :as tokens]))

(deftest jwt-test
  (let [claims {:subscriber-id (random-uuid) :exp 1234}
        token  (tokens/process :sign "secret" claims)]
    (is (= 3 (count (str/split token #"\."))))
    (is (= (update claims :subscriber-id str)
           (tokens/process :unsign "secret" token)))
    (is (nil? (tokens/process :unsign "wrong-secret" token)))
    (is (nil? (tokens/process :unsign "secret" (str token "x"))))))
