(ns discljord.messaging
  (:require [clojure.spec.alpha :as s]
            [discljord.spec :as ds]
            [discljord.connections :refer [api-call]]
            [clojure.core.async :as async]
            [clojure.data.json :as json]
            [org.httpkit.client :as http]
            [clojure.string :as str]))

(defn headers
  [token]
  {"Authorization" (str "Bot " (str/trim token))
   "User-Agent" "DiscordBot (https://github.com/IGJoshua/discljord, 0.1.0-SNAPSHOT)"
   "Content-Type" "application/json"})

(defn message
  [bot channel-id content]
  ;; TODO: Make it rate-limit friendly
  (http/post (api-call (str "channels/" channel-id "/messages"))
             {:body (json/write-str {:content content
                                     :nonce (str (System/currentTimeMillis))
                                     :tts false})
              :headers (headers (get-in @bot [::ds/token]))}))
