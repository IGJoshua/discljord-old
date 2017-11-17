(ns discljord.connections
  (:require [clojure.spec.alpha :as s]
            [discljord.spec :as ds]
            [clojure.core.async :as async]
            [clojure.data.json :as json]
            [org.httpkit.client :as http]
            [gniazdo.core :as ws]
            [clojure.string :as str]))

(defn api-call
  [gateway]
  (str "https://discordapp.com/api/" gateway "?v=6&encoding=json"))
(s/fdef api-call
        :args string?
        :ret string?)

(defn create-connection
  [token event-channel]
  (let [response @(http/get (api-call "gateway/bot")
                            {:headers
                             {"Authorization" (let [token (str/trim token)]
                                                (if (= 0 (str/index-of token "Bot "))
                                                  token
                                                  (str "Bot " token)))}})
        body (json/read-str (:body response))
        gateway (get body "url")
        shard-count (get body "shards")
        interval nil
        result {::ds/shards (vec (for [id (range 0 shard-count)]
                                   {::ds/websocket {::ds/gateway gateway
                                                    ::ds/hb-interval interval
                                                    ::ds/ack? true}
                                    ::ds/shard-id [id shard-count]}))
                ::ds/shard-count shard-count}]
    result))
(s/fdef create-connection
        :args (s/cat :t ::token)
        :ret ::ds/connection)

(defn disconnect!
  "Loops through all shards in the given bot, disconnects them, swaps the bot atom to dissoc the connection, and returns the new bot map."
  [bot]
  (async/>!! (get-in @bot [::ds/event-channel]) {::ds/event-type :disconnect ::ds/event-data {}})
  (doseq [shard-id (range 0 (get-in @bot [::ds/connection ::ds/shard-count]))]
    (when-let [socket (get-in @bot [::ds/connection ::ds/shards shard-id ::ds/websocket ::ds/ws-client])]
      (ws/close socket)))
  (swap! bot dissoc ::ds/connection)
  @bot)

(defn heartbeat
  [bot shard-id]
  (swap! bot
         #(do (ws/send-msg (-> %
                               ::ds/connection
                               ::ds/shards
                               (get shard-id)
                               ::ds/websocket
                               ::ds/ws-client)
                           (json/write-str {:op 1 :d (-> %
                                                         ::ds/connection
                                                         ::ds/shards
                                                         (get shard-id)
                                                         ::ds/websocket
                                                         ::ds/last-message
                                                         ::ds/seq)}))
              (assoc-in % [::ds/connection
                           ::ds/shards
                           shard-id
                           ::ds/websocket
                           ::ds/ack?]
                        false))))

(declare reconnect-socket!)

(defn event-keys
  [event]
  (case event
    "READY" :ready
    "RESUMED" :resumed
    "GUILD_SYNC" :guild-sync
    "GUILD_CREATE" :guild-create
    "GUILD_DELETE" :guild-delete
    "GUILD_UPDATE" :guild-update
    "GUILD_MEMBER_ADD" :guild-member-add
    "GUILD_MEMBER_REMOVE" :guild-member-remove
    "GUILD_MEMBER_UPDATE" :guild-member-update
    "GUILD_MEMBERS_CHUNK" :guild-members-chunk
    "GUILD_ROLE_CREATE" :guild-role-create
    "GUILD_ROLE_DELETE" :guild-role-delete
    "GUILD_ROLE_UPDATE" :guild-role-update
    "GUILD_BAN_ADD" :guild-ban-add
    "GUILD_BAN_REMOVE" :guild-ban-remove
    "CHANNEL_CREATE" :channel-create
    "CHANNEL_DELETE" :channel-delete
    "CHANNEL_UPDATE" :channel-update
    "CHANNEL_PINS_UPDATE" :channel-pins-update
    "MESSAGE_CREATE" :message-create
    "MESSAGE_DELETE" :message-delete
    "MESSAGE_UPDATE" :message-update
    "MESSAGE_DELETE_BULK" :message-delete-bulk
    "MESSAGE_REACTION_ADD" :message-reaction-add
    "MESSAGE_REACTION_REMOVE" :message-reaction-remove
    "MESSAGE_REACTION_REMOVE_ALL" :message-reaction-remove-all
    "USER_UPDATE" :user-update
    "USER_NOTE_UPDATE" :user-note-update
    "USER_SETTINGS_UPDATE" :user-settings-update
    "PRESENCE_UPDATE" :presence-update
    "VOICE_STATE_UPDATE" :voice-state-update
    "TYPING_START" :typing-start
    "VOICE_SERVER_UPDATE" :voice-server-update
    "RELATIONSHIP_ADD" :relationship-add
    "RELATIONSHIP_REMOVE" :relationship-remove
    :unknown))

(defn proc-message
  [bot shard-id payload]
  (async/go
    (let [event-channel (-> @bot ::ds/event-channel)
          recieved (json/read-str payload)
          op (get recieved "op")
          type (get recieved "t")
          data (get recieved "d")
          seq (get recieved "s")]
      (when-not (nil? seq)
        (swap! bot #(assoc-in % [::ds/connection
                                 ::ds/shards
                                 shard-id
                                 ::ds/websocket
                                 ::ds/last-message]
                              {::ds/content recieved ::ds/seq seq})))
      (case op
        10 (swap! bot #(assoc-in % [::ds/connection
                                    ::ds/shards
                                    shard-id
                                    ::ds/websocket
                                    ::ds/hb-interval]
                                 (get data "heartbeat_interval")))
        1 (heartbeat bot shard-id)
        7 (reconnect-socket! bot shard-id)
        9 (throw (Exception. "Invalid session id!"))
        11 (swap! bot #(assoc-in % [::ds/connection
                                    ::ds/shards
                                    shard-id
                                    ::ds/websocket
                                    ::ds/ack?]
                                 true))
        0 (async/>! (get-in @bot [::ds/event-channel]) {::ds/event-type (event-keys type)
                                                        ::ds/event-data data})
        :else (throw (Exception. "Unknown payload"))))))

(declare connect!)

(defn connect-socket!
  [bot shard-id]
  (let [gateway (str (get-in @bot [::ds/connection ::ds/shards shard-id ::ds/websocket ::ds/gateway])
                     "?v=6&encoding=json")]
    (ws/connect gateway
       :on-receive (partial proc-message bot shard-id)
       :on-connect (fn [session]
                     (async/go-loop []
                       (let [ready (async/<! (get-in @bot [::ds/event-channel]))]
                         (if (= :ready (::ds/event-type ready))
                           (do (async/>! (get-in @bot [::ds/event-channel]) ready)
                               (swap! bot #(assoc-in % [::ds/connection
                                                        ::ds/shards
                                                        shard-id
                                                        ::ds/websocket
                                                        ::ds/session-id]
                                                     (get (::ds/event-data ready) "session_id"))))
                           (do (async/>! (get-in @bot [::ds/event-channel]) ready)
                               (recur)))))
                     (async/go-loop [keep-alive true]
                       (when (and keep-alive (get-in @bot [::ds/connection]))
                         ;; Have we gotten the initial handshake back?
                         (if (nil? (-> @bot
                                       ::ds/connection
                                       ::ds/shards
                                       (get shard-id)
                                       ::ds/websocket
                                       ::ds/hb-interval))
                           ;; Wait till we get the initial handshake back
                           (do (async/<! (async/timeout 100))
                               (recur true))
                           (if-not (-> @bot
                                       ::ds/connection
                                       ::ds/shards
                                       (get shard-id)
                                       ::ds/websocket
                                       ::ds/ack?)
                             (do (reconnect-socket! bot shard-id)
                                 (recur false))
                             (do (heartbeat bot shard-id)
                                 (async/<! (async/timeout (-> @bot
                                                              ::ds/connection
                                                              ::ds/shards
                                                              (get shard-id)
                                                              ::ds/websocket
                                                              ::ds/hb-interval)))
                                 (recur true)))))))
       :on-error #(println "Error: " %)
       :on-close (fn [code reason]
                   (println code reason)
                   (case code
                     4000 (reconnect-socket! bot shard-id) ;; unknown error
                     4001 (do (disconnect! bot)
                              (throw (Exception. "Improper op code sent to server! Disconnecting to prevent future errors!")))
                     4002 (do (disconnect! bot)
                              (throw (Exception. "Improper payload sent to server! Disconnecting to prevent future errors!")))
                     4003 (do (disconnect! bot)
                              (throw (Exception. "Payload sent before authentication! Disconnecting!")))
                     4004 (do (disconnect! bot)
                              (throw (Exception. "Token was invalid! Disconnecting!")))
                     4005 (do (throw (Exception. "Multiple identify payloads sent!")))
                     4007 (do (disconnect! bot)
                              (throw (Exception. "Invalid seq sent! Disconnecting!")))
                     4008 (do (disconnect! bot)
                              (throw (Exception. "Rate limited! Disconnecting!")))
                     4009 (reconnect-socket! bot shard-id) ;; session timeout
                     4010 (do (throw (Exception. "Invalid shard!")))
                     4011 (do (binding [*out* *err*]
                                (println "Session too big. Disconnecting bot, waiting 10 seconds, and sharding."))
                              (disconnect! bot)
                              (Thread/sleep 10000)
                              (connect! bot)
                              #_(throw (Exception. "Too small a socket for the shard! Disconnecting!"))))))))

(defn reconnect-socket!
  "Takes an atom with a bot and modifies it to attempt to reconnect"
  [bot shard-id]
  (ws/close (get-in @bot
                    [::ds/connection
                     ::ds/shards
                     shard-id
                     ::ds/websocket
                     ::ds/ws-client]))
  (swap! bot #(assoc-in % [::ds/connection
                           ::ds/shards
                           shard-id
                           ::ds/websocket
                           ::ds/ws-client]
                        (connect-socket! bot shard-id)))
  (when-let [socket (get-in @bot
                            [::ds/connection
                             ::ds/shards
                             shard-id
                             ::ds/websocket
                             ::ds/ws-client])]
    (ws/send-msg socket
                 (json/write-str {:op 6, :d {"token" (get-in @bot [::ds/token])
                                             "session_id" (get-in @bot [::ds/connection
                                                                        ::ds/shards
                                                                        shard-id
                                                                        ::ds/websocket
                                                                        ::ds/session-id])
                                             "seq" (get-in @bot [::ds/connection
                                                                 ::ds/shards
                                                                 shard-id
                                                                 ::ds/websocket
                                                                 ::ds/last-message
                                                                 ::ds/seq])}}))))

(defn connect!
  "Takes an atom with a bot in it, and creates and connects a websocket to Discord. This will take about one second extra per suggested shard from Discord. Returns the value of the bot after the changes."
  [bot]
  (swap! bot assoc ::ds/connection (create-connection (::ds/token @bot) (::ds/event-channel @bot)))
  (doseq [shard-id (range 0 (get-in @bot [::ds/connection ::ds/shard-count]))]
    (do (swap! bot #(assoc-in % [::ds/connection
                                 ::ds/shards
                                 shard-id
                                 ::ds/websocket
                                 ::ds/ws-client]
                              (connect-socket! bot shard-id)))
        (Thread/sleep 1000)
        (when-let [socket (get-in @bot
                             [::ds/connection
                              ::ds/shards
                              shard-id
                              ::ds/websocket
                              ::ds/ws-client])]
                 (ws/send-msg socket
                              (json/write-str {:op 2, :d {"token" (get-in @bot [::ds/token])
                                                          "properties" {"$os" "linux"
                                                                        "$browser" "discljord"
                                                                        "$device" "discljord"}
                                                          "compress" false}})))
        nil))
  @bot)
