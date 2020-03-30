(ns codiv-spread.core
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.walk :as walk]
            [oz.core :as oz]))

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Tabla comparativa"
                          :data {}
                          :selected-countries #{"Mexico"}}))

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

(defn app []
  [:div
   [:h1 "Tabla comparativa del aumento diario de casos de COVID-19"]
   [:p "Esta tabla describe la taza de crecimiento diaria de casos. Los valores se obtienen diviendo el numero de casos de cada día sobre el numero de casos del día anterior. Si el valor es 1 eso quiere decir que no hay casos nuevos. Mientras mayor sea el valor más rápido se esparce el virus en la población."]
   [:p "Nota: cada una de las gráficas comienza al día siguiente a partir de que se detectó el primer en caso en el país correspondiente."]
   [:select
    {:on-change (fn [e] (->> e
                            .-target
                            .-value
                            (swap! app-state update :selected-countries conj)))}
    (->> @app-state :data keys
         sort
         (map
          (fn [c] [:option {:key c} c])))]
   [:div (->> @app-state :selected-countries
              (map (fn [c]
                     [:button
                      {:key c
                       :on-click
                       (fn [_]
                         (swap! app-state update :selected-countries
                                (fn [cs] (set (remove #(= % c) cs)))))}
                      [:span {:class "selected-countries__remove"} "x   "]
                      c])))]
   [:div
    [oz/vega-lite
     {:data {:values (apply prepare-data (@app-state :selected-countries))}
      :encoding {:x {:field "Día" :type "quantitative"}
                 :y {:field "Crecimiento" :type "quantitative"}
                 :color {:field "País" :type "nominal"}}
      :mark "line"
      :width 500
      :height 400}]]
   [:a {:href "https://github.com/pomber/covid19"
        :target "_blank"} "Fuente de los datos"]
   ])

(defn start []
  (-> "https://pomber.github.io/covid19/timeseries.json"
      js/fetch
      (.then #(.json %))
      (.then #(swap! app-state assoc :data (->> % js->clj
                                                (map walk/keywordize-keys)
                                                (into {})))))
  (reagent/render-component [app]
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
