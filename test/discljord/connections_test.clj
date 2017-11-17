(ns discljord.connections-test
  (:use [org.httpkit.fake])
  (:require [discljord.connections :refer :all]
            [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [discljord.spec :as ds]
            [clojure.data.json :as json]))

(deftest ws-api
  (testing "Is an api call correctly created from a gateway?"
    (is (= "https://discordapp.com/api/gateway/bot?v=6&encoding=json"
           (api-call "gateway/bot"))))
  (testing "Is a connection properly created?"
    (with-fake-http ["https://discordapp.com/api/gateway/bot?v=6&encoding=json"
                     (json/write-str {"url" "wss://fake.gateway.api/" "shards" 1})])))
