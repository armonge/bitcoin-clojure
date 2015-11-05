(ns bitcoin.app
  (:use [jayq.core :only [ajax]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [jayq.macros :refer [let-ajax]]
                   )
  (:require [reagent.core :as r :refer [atom]]
            [cljs.core.async :as async :refer [<! put! chan close!]]
            [cljs.core :as cljs :refer [js->clj]]
            [cljs-time.core :refer [now]]
            [cljs-time.coerce :refer [from-long to-long from-string]]
            [cljs-time.format :as time-fmt]
            [cljsjs.d3]
            [nv]))

(enable-console-print!)
(defonce prices (r/atom (vector)))
(def interval 10000)

(defn timeout [ms]
  (let [c (chan)]
    (js/setTimeout (fn [] (close! c)) ms)
    c))

(defn request [url]
  (let [c (chan)]
    (let-ajax [response {:url url
                         :dataType :json}]
      (put! c response))
  c))


(defn format-prices [prices]
  "Returns the prices in a format good to be used for nv"
  (let [prices (take-last 10 prices)]
    (map  (fn [price] (js-obj 
                        "x" (to-long (get price :date) )
                        "y" (get price :price)))
          prices)))

(defn price-elem [{price :price id :id}]
  "Renders a new price"
  [:li {:id (str "price-" id)} price])

(defn price-list
  "Renders the list of prices"
  []
  (let [prices (take-last 10 @prices)]
    [:div.col-md-2.col-md-pull-10
     [:h3 "Last " (count prices) " prices"]
     [:ul
      (for [item prices ]
        ^{:key item} [price-elem item])]]))

(defn vizualization 
  "Renders a d3 graph"
  []

  [:div#d3-node.col-md-10.col-md-push-2 {:style {:height "420"}}  [:svg ]])

(defn redraw [prices] 
  (.addGraph js/nv (fn []
                     (let [chart (.. js/nv -models lineChart
                                     (margin (js-obj "left" 100))
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

                          (let [my-data (format-prices prices)]
                            (.. js/d3 (select "#d3-node svg")
                                (datum (clj->js [{:values my-data
                                                  :key "Price over time"
                                                  :color "red"
                                                  }]))
                                (call chart)))
                          ))))

; Makes a request every 5 seconds to coindesk
(go-loop []
    (let [response (<! (request "https://api.coindesk.com/v1/bpi/currentprice.json"))
          response (js->clj response)
          last-price (last @prices)
          price {:price (get-in response ["bpi" "USD" "rate_float"]) 
                            :date (from-string (get-in response ["time" "updatedISO"])) }]

      ; only when it's an actual new value
      (when-not (cljs-time.core/= (get last-price :date) (get price :date))
        (swap! prices conj price)
        (redraw (take-last 10 @prices)))
      (<! (timeout interval)))
    (recur ))

; i want to show the vizualization if there are already some prices loaded
(if @prices 
    (redraw @prices))

(defn container []
  [:div.row
    [vizualization]
    [price-list]])

(defn init []
  (r/render-component [container] (.getElementById js/document "container")))
