(ns example.echo-bot
  (:require [discljord.core :as discord :refer [defcommands]]
            [discljord.spec :as ds])
  (:import [java.lang IllegalArgumentException])
  (:gen-class))

(def token (slurp "resources/token.txt"))
(def main-bot (atom (discord/create-bot {:token token
                                         :info {::ds/author {::ds/name {::ds/name-type ::ds/full-name
                                                                            ::ds/first-name "Joshua"
                                                                            ::ds/last-name "Suskalo"}
                                                                 ::ds/email "JZSuskalo@Student.FullSail.edu"}
                                                    ::ds/version "0.1.0-SNAPSHOT"}})))

(defcommands main-bot
  {:keys [user channel contents] :as params}
  {"echo" {:help-text "Echos back whatever was said in the command"
           :callback (discord/message channel (str (discord/mention user) contents))}
   "exit" {:help-text "Makes this bot quit"
           :callback (System/exit 0)}})

(defn -main
  []
  (discord/connect! main-bot))
