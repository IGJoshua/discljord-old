(ns discljord.messaging
  (:require [clojure.spec.alpha :as s]
            [discljord.spec :as ds]
            [discljord.connections :refer [api-call]]
            [clojure.core.async :as async]
            [clojure.data.json :as json]
            [org.httpkit.client :as http]
            [clojure.string :as str]))

