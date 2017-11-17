(ns discljord.bots
  (:require [clojure.spec.alpha :as s]
            [clojure.core.async :as async]
            [discljord.spec :as ds]))

(def prev-id (atom -1))
(defn next-id
  []
  (swap! prev-id inc))

(defn create-bot
  "Creates and returns a map defining a bot using a particular token."
  [{:keys [token info listeners] :as params}]
  {::ds/id (next-id)
   ::ds/token token
   ::ds/event-channel (async/chan 100)
   ::ds/info info
   ::ds/state {}
   ::ds/listeners listeners})
(s/fdef create-bot
        :args (s/keys :req [::ds/token]
                      :opt [::ds/info ::ds/state ::ds/listeners])
        :ret ::ds/bot)
