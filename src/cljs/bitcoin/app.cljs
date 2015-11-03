(ns bitcoin.app
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r :refer [atom]]
            [cljs.core.async :as async :refer [>! <! put! chan alts! close!]]
            [cljs.core :as cljs :refer [js->clj]]
            [cljsjs.d3]
            [cljsjs.pusher]))

(enable-console-print!)
(defonce ticks (r/atom (sorted-map)))

; CHANNEL: live_trades, EVENT: trade, PUSHER KEY: de504dc5763aeef9ff52
(def socket (new js/Pusher "de504dc5763aeef9ff52" ))
(def ticker-channel (.subscribe socket "live_trades"))

(defn add-tick [{id "id" price "price"}] 
  "Adds a new tick from a pusher event"
  (print "add-tick" id price)
  (swap! ticks assoc id {:id id :price price}))

(defn pusher-channel->event
  "Given a pusher socket, channel and event name returns a core.async channel of observed events.
  Can supply the core.async channel as an optional third argument"
  ([socket channel event-name] (pusher-channel->event socket channel event-name (chan)))
  ([socket channel event-name c]
   (.bind channel event-name (fn [event] (let [event (js->clj event)]
                                           (put! c (js->clj event)))))
   c))

(go-loop []
         (let [c (pusher-channel->event socket ticker-channel "trade")
               tick (<! c)]
           (add-tick tick)
           (recur)))

(defn price-elem [{price :price id :id}]
  "Renders a new price"
  [:li {:id (str "price-" id)} price])

(defn price-list []
  [:div.col-md-3
   [:h3 "Last 5 prices"]
   [:ul
    (for [item (take-last 5 (vals @ticks) )]
      ^{:key item} [price-elem item])]])

(defn vizualization [] 
  []
  (println ticks)
  )

(defn container []
  [:div.row
   [price-list]
   [vizualization]]
  )

(defn init []
  (r/render-component [container] (.getElementById js/document "container")))
