(ns discljord.core
  (:require [clojure.spec.alpha :as s]
            [discljord.spec :as ds]
            [discljord.bots :as bots]
            [discljord.connections :as conn]))

(defn connect!
  [bot]
  (conn/connect! bot))

(defn disconnect!
  [bot]
  (conn/disconnect! bot))

(defn create-bot
  [opts]
  (bots/create-bot opts))

(defn- defcommands-helper
  "Helper function for defcommands. Does all the heavy lifting,
   the macro just ensures that the params aren't evaluated and
   that it's called at compile-time."
  [bot k l forms]
  nil)

(defmacro defcommands
  "A macro to help define and register a set of commands for a given bot.

   The first parameter is the bot that will be registered to. The second
   argument is a symbol or destructuring form to which the data from the
   message will be assigned, and finally you can pass any number of
   commands afterwards.

   Commands themselves are represented as two values, a string representing
   what is to be matched at the beginning of the command, and a map with
   two required keys. The first key is :help-text and is a string that is
   displayed when the auto-generated help command is run"
  ([bot l forms]
   (defcommands-helper bot :create-message l forms))
  ([bot k l forms]
   (defcommands-helper bot k l forms)))
