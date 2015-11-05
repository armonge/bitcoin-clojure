(set-env!
 :source-paths    #{"src/cljs"}
 :resource-paths  #{"resources"}
 :dependencies '[[adzerk/boot-cljs          "0.0-3308-0" :scope "test"]
                 [adzerk/boot-cljs-repl     "0.1.9"      :scope "test"]
                 [adzerk/boot-reload        "0.3.1"      :scope "test"]
                 [pandeiro/boot-http        "0.6.3"      :scope "test"]
                 [org.clojure/core.async    "0.2.371"]
                 [org.clojure/clojurescript "1.7.58"]
                 [com.andrewmcveigh/cljs-time "0.3.14"]
                 [cljsjs/d3   "3.5.5-3"]
                 [reagent     "0.5.0"]
                 [jayq "2.5.4"]])

(require
  '[adzerk.boot-cljs      :refer [cljs]]
  '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
  '[adzerk.boot-reload    :refer [reload]]
  '[pandeiro.boot-http    :refer [serve]])

(deftask build []
  (comp (speak)
        (cljs)))

(deftask run []
  (comp (serve)
        (watch)
        (cljs-repl)
        (reload)
        (build)))

(deftask production []
  (task-options! cljs {:optimizations :simple
                      :source-map true })
  (task-options! serve {:dir "target" })
  identity)

(deftask development []
  (task-options! cljs {:optimizations :none
                       :unified-mode true
                       :source-map true}
                 reload {:on-jsload 'bitcoin.app/init})
  identity)

(deftask dev
  "Simple alias to run application in development mode"
  []
  (comp (development)
        (run)))


