(ns discljord.spec
  (:require [clojure.spec.alpha :as s]))

;; ========================================
;; Specs common to bots and connections

(s/def ::first-name string?)
(s/def ::last-name string?)
(s/def ::initials string?)

(s/def ::username string?)

(s/def ::full-name (s/keys :req [::first-name ::last-name]
                           :opt [::initials ::username]))

(s/def ::name (s/or :username (s/keys :req [::username])
                    :full-name ::full-name))

(def website-regex #"[-a-zA-Z0-9@:%._\+~#=]{2,256}\.[a-z]{2,6}\b([-a-zA-Z0-9@:%_\+.~#?&//=]*)")
(s/def ::website (s/and string? #(re-matches website-regex %)))

(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")
(s/def ::email (s/and string? #(re-matches email-regex %)))

(s/def ::author (s/keys :req [::name]
                        :opt [::email ::website]))

(s/def ::full-lisence string?)
(s/def ::lisence-type string?)
(s/def ::lisence (s/keys :req [::lisence-type ::full-lisence]))
(s/def ::version (s/or :version-string string?
                       :version-number number?))

(s/def ::framework (s/keys :req [::author ::version]
                           :opt [::lisence]))
(s/def ::info (s/nilable (s/keys :req [::author ::version]
                                 :opt [::framework ::lisence])))

(s/def ::id number?)

(s/def ::ws-client any?)
(s/def ::gateway string?)
(s/def ::hb-interval any?)

(s/def ::content map?)
(s/def ::last-message (s/keys :req [::content ::seq]))

(s/def ::ack? boolean?)

(s/def ::websocket (s/keys :req [::gateway ::hb-interval]
                           :opt [::ws-client ::last-message ::ack?]))

(s/def ::shard-count number?)

(s/def ::shard-id (s/tuple number? number?))
(s/def ::shard (s/keys :req [::websocket ::shard-id]))
(s/def ::shards (s/coll-of ::shard))
(s/def ::connection (s/keys :req [::shards ::shard-count]))

(s/def ::callback fn?)
(s/def ::state map?)

(s/def ::event-type keyword?)
(s/def ::event-data map?)
(s/def ::event (s/keys :req [::event-type ::event-data]))

(s/def ::token string?)
(s/def ::event-channel any?)
(s/def ::listener (s/keys :req [::event-type ::callback ::event-channel]))
(s/def ::listeners (s/nilable (s/map-of keyword? ::listener)))
(s/def ::bot (s/keys :req [::id ::token ::event-channel]
                     :opt [::connection ::info ::state ::listeners]))


