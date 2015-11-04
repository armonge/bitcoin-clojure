(ns bitcoin.app
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r :refer [atom]]
            [cljs.core.async :as async :refer [>! <! put! chan alts! close!]]
            [cljs.core :as cljs :refer [js->clj]]
            [cljs-time.core :refer [now]]
            [cljs-time.coerce :refer [from-long to-long]]
            [cljs-time.format :as time-fmt]
            [cljsjs.pusher]))

(enable-console-print!)
(defonce ticks (r/atom (sorted-map)))

; CHANNEL: live_trades, EVENT: trade, PUSHER KEY: de504dc5763aeef9ff52
(def socket (new js/Pusher "de504dc5763aeef9ff52" ))
(def ticker-channel (.subscribe socket "live_trades"))

(defn add-tick [{id :id price :price}] 
  "Adds a new tick from a pusher event"
  (let [tick {:id id :price price :date (now)}]
    (swap! ticks assoc id tick)))

(defn format-ticks [ticks]
  "Returns the ticks in a format good to be used for nv"
  (let [ticks (take-last 100 (vals ticks))]
    (mapv  (fn [tick] (js-obj "x" (to-long (get tick :date) ) "y" (get tick :price))) ticks)))

(defn pusher-channel->event
  "Given a pusher socket, channel and event name returns a core.async channel of observed events.
  Can supply the core.async channel as an optional third argument"
  ([socket channel event-name] (pusher-channel->event socket channel event-name (chan)))
  ([socket channel event-name c]
   (.bind channel event-name (fn [event] (let [event (js->clj event :keywordize-keys true)]
                                           (put! c event))))
   c))

(defn price-elem [{price :price id :id}]
  "Renders a new price"
  [:li {:id (str "price-" id)} price])

(defn price-list []
  "Renders the list of prices"
  [:div.col-md-2.col-md-pull-10
   [:h3 "Last 5 prices"]
   [:ul
    (for [item (take-last 5 (vals @ticks) )]
      ^{:key item} [price-elem item])]])

(defn vizualization [] 
  "Renders a d3 graph"
  []
  (.addGraph js/nv (fn []
                     (let [chart (.. js/nv -models lineChart
                                     (margin #js {:left 100})
                                     (useInteractiveGuideline true)
                                     (transitionDuration 350)
                                     (showLegend true)
                                     (showYAxis true)
                                     (showXAxis true))]
                       (.. chart -xAxis 
                           (axisLabel "Date")
                           (tickFormat (fn [value] (time-fmt/unparse (time-fmt/formatter "HH:mm:ss") (from-long  value)))))
                       (.. chart -yAxis 
                           (axisLabel "Price") 
                           (tickFormat (.format js/d3 "$ ,r")))

                      (go-loop []
                       (let [c (pusher-channel->event socket ticker-channel "trade")
                             tick (<! c)]
                         ; (let [my-data (format-ticks @ticks)]
                         (add-tick tick)
                         (let [my-data (format-ticks @ticks)]
                           (.. js/d3 (select "#d3-node svg")
                               (datum (clj->js [{:values my-data
                                                 :key "Price"
                                                 :color "red"
                                                 }]))
                               (call chart))))
                         (recur)))))
  [:div#d3-node.col-md-10.col-md-push-2 {:style {:height "420"}}  [:svg ]])



(defn container []
  [:div.row
   [vizualization]
   [price-list]]
  )

(defn init []
  (r/render-component [container] (.getElementById js/document "container")))
