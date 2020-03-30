(ns codiv-spread.core
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.walk :as walk]
            [oz.core :as oz]))

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Tabla comparativa"
                          :data {}}))

(defn calculate-growth-rate* [country-data]
  (reduce (fn [{:keys [confirmed growth]} val*]
            {:confirmed (conj confirmed (val* :confirmed))
             :growth (conj growth
                           (if (> (last confirmed) 0)
                             (float (/ (val* :confirmed)
                                       (last confirmed)))
                             0))})
          {:confirmed [0]
           :growth [0]}
          country-data))

(defn calculate-growth-rate [countries]
  (into {}
        (map (fn [[country data]]
               [country (calculate-growth-rate* data)])
             countries)))
(defn spy [x] (println x) x)

(defn prepare-data [& countries]
  (mapcat (fn [country]
         (-> @app-state
             :data
             calculate-growth-rate
             (get country)
             :growth
             (->>
              (drop-while #(= 0 %))
              (map-indexed (fn [i val]
                             {"País" country
                              "Día" i
                              "Crecimiento" val})))))
   countries))

(defn hello-world []
  [:div {:style {:width "500px"}}
   [:h1 "Tabla comparativa del aumento diario de casos de COVID-19"]
   [oz/vega-lite
    {:data {:values (prepare-data "Mexico" "Spain" "US" "Italy")}
     :encoding {:x {:field "Día" :type "quantitative"}
                :y {:field "Crecimiento" :type "quantitative"}
                :color {:field "País" :type "nominal"}}
     :mark "line"
     :width 500
     :height 500
     :axis {:offset 1}}]
   ])
(println )
(-> @app-state
    :data
    calculate-growth-rate
    (get "Mexico")
    :growth
    (->> (map-indexed (fn [i val]
                        {:dia (inc i)
                         :crecimiento val}))))

(-> @app-state
    :data
    calculate-growth-rate
    (get "Mexico")
    )

(defn start []
  (-> "https://pomber.github.io/covid19/timeseries.json"
      js/fetch
      (.then #(.json %))
      (.then #(swap! app-state assoc :data (->> % js->clj
                                                (map walk/keywordize-keys)
                                                (into {})))))
  (reagent/render-component [hello-world]
                            (. js/document (getElementById "app"))))

(defn ^:export init []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds
  (start))

(defn stop []
  ;; stop is called before any code is reloaded
  ;; this is controlled by :before-load in the config
  (js/console.log "stop"))
