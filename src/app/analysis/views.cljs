(ns app.analysis.views
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [app.events :as events]
   [app.subs :as subs]
   [clojure.string :as string]
   [re-com.core :refer [at selection-list]]
   [app.utils.views :refer [button <sub dropdown court]]
   [app.utils :refer [court-dimensions hoop-coordinates sin-turns cos-turns polar-hoop->eucl-court]]))


(defn draw-sector
  ([sector]
   (draw-sector {} sector))
  ([attrs {:keys [max-distance min-angle max-angle]}]
   (let [[hoop-x hoop-y] hoop-coordinates
         min-y (- hoop-y 24)
         [min-dx min-dy] [(* max-distance (sin-turns min-angle)) (* max-distance (cos-turns min-angle))]
         [arc-x arc-y] [(+ hoop-x (* max-distance (sin-turns (min max-angle 0.25))))
                        (+ hoop-y (* max-distance (cos-turns (min max-angle 0.25))))]
         d (string/join " "
                        (flatten
                         [(if (= min-angle -0.5)
                            [\M hoop-x min-y
                             \l (- max-distance) 0
                             \l 0 (- hoop-y min-y)]
                            [\M hoop-x hoop-y
                             \l min-dx min-dy])
                          \A max-distance max-distance 0 0 0 arc-x arc-y
                          (if (= max-angle 0.5)
                            [\L (+ hoop-x max-distance) min-y
                             \L hoop-x min-y
                             \Z]

                            [\L hoop-x hoop-y
                             \Z])]))]
     [:path (assoc attrs :d d)])))


(def orange-purple-divergent
  ["#6665de"
   "#827be2"
   "#9b91e5"
   "#b2a8e9"
   "#c8c0ec"
   "#dcd8ee"
   "#f3ddcc"
   "#f3c9a9"
   "#f1b686"
   "#eca363"
   "#e5903f"
   "#dd7d11"])


(def red-green-divergent
  ["#d43d51"
   "#e0636b"
   "#eb8387"
   "#f3a3a4"
   "#f9c2c1"
   "#fde0e0"
   "#dbebe5"
   "#b7d7cc"
   "#93c3b3"
   "#6eaf9a"
   "#469b83"
   "#00876c"])


(def purple-single
  ["#edf2ff"
   "#d2d9ec"
   "#b9c1d9"
   "#9fa9c7"
   "#8792b4"
   "#6f7ba2"
   "#576590"
   "#40507e"
   "#273c6d"])


(defn pallete->color
  [pallete x]
  (get pallete (->> (keys pallete)
                    (filter #(< x %))
                    (reduce min))
       (second (reduce max pallete))))


(defn pps->color
  [pps]
  (if (or (nil? pps) (NaN? pps))
    "white"
    (pallete->color
     (zipmap (iterate #(+ % 0.1) 0.5 #_0.45)
             red-green-divergent)
     pps)))


(defn freq->color
  [percent]
  (if (or (nil? percent) (NaN? percent) (zero? percent))
    "white"
    (pallete->color
     (zipmap (let [step 0.025]
               (iterate #(+ % step) step))
             purple-single)

     percent)))


(defn shot-information
  [hovered? shot-info]
  (when @hovered?
    (let [{:keys [game team action x y]} @shot-info
          y (min (- (* 12 42) 45) (max 10 y))
          {:game/keys [home-team away-team datetime]} game
          team-home? (= team home-team)
          {opponent-name :team/name} (if team-home? away-team home-team)
          {:action/keys [player]} action
          {team-name :team/name} team
          width (first court-dimensions)
          right-side? (> x (/ width 2))]
      [:text
       {:text-anchor (if right-side? "end" "start")}
       [:tspan
        {:x x :dx (if right-side? -15 15) :y y}
        (str "#" player " " team-name (if team-home? " vs. " " @ ") opponent-name)]
       [:tspan
        {:x x :dx (if right-side? -15 15) :y y :dy 20}
        (str (quot (:shot/distance action) 12) "'" (rem (:shot/distance action) 12)  "\", " (.toFixed (:shot/angle action) 3) "tr")]
       (when datetime
         [:tspan
          {:x x :dx (if right-side? -15 15) :y y :dy 40}
          (second (string/split (.toDateString datetime) #" " 2))])])))


(defn sector-information
  [sector-hovered? sector-info]
  (when @sector-hovered?
    (let [{total-shots :shots total-pts :pts} (<sub [::subs/shot-data])
          {:keys [pps shots pts min-distance max-distance]
           :or {pps ##NaN shots 0 pts 0}} @sector-info
          [_ court-y] court-dimensions]
      [:text
       [:tspan
        {:x 10 :y (- court-y 70)}
        (str (quot min-distance 12) \' (rem min-distance 12) "\" - "
             (quot max-distance 12) \' (rem max-distance 12) \")]
       [:tspan
        {:x 10 :y (- court-y 50)}
        (str "PPS: " (.toFixed pps 2))]
       [:tspan
        {:x 10 :y (- court-y 30)}
        (str "Shots: " shots " (" (.toFixed (* 100 (/ shots total-shots)) 1) "%)")]
       [:tspan
        {:x 10 :y (- court-y 10)}
        (str "Points: " pts " (" (.toFixed (* 100 (/ pts total-pts)) 1) "%)")]])))


(defn shot
  [{:keys [on-click on-mouse-enter on-mouse-leave]} action pts]
  (let [{:shot/keys [angle distance]} action
        [x y] (polar-hoop->eucl-court hoop-coordinates [angle distance])
        icon-size 4
        color (get {0 "#ff5349"
                    1 "#ace1af"
                    2 "#3cd070"
                    3 "#008000"
                    4 "#8a2be2"}
                   pts "gray")]
    [:g
     [:circle
      {:r icon-size :cx x :cy y
       :fill-opacity 0
       :stroke color
       :stroke-width 1
       :on-mouse-enter (on-mouse-enter x y)
       :on-click (on-click x y)
       :on-mouse-leave on-mouse-leave}]]))


(defn shot-chart
  []
  (let [selected? (reagent/atom false)
        shot-info (reagent/atom {})
        sector-info (reagent/atom {})
        sector-selected? (reagent/atom false)
        {total-pts :pts total-shots :shots} (<sub [::subs/shot-data])
        zone-by (<sub [::subs/zone-by])
        shot-data-by-sector (<sub [::subs/shot-data-by-sector])]
    [court
     {:id "shot-chart"
      :width "max-h-96"}
     (when (<sub [::subs/show-zones?])
       [:g {:stroke-width 0.5 :stroke "black"}
        (let [sector-pps-map (->> shot-data-by-sector
                                  (map (juxt :sector #(dissoc % :sector)))
                                  (into {}))]
          (for [sector (sort-by :max-distance > (<sub [::subs/sectors]))]
            (let [{:keys [pps shots pts]
                   :or {shots 0 pts 0}
                   :as sector-data} (get sector-pps-map sector)]
              ^{:key (gensym "key-")}
              [draw-sector {:on-mouse-enter (fn [e]
                                              (reset! sector-info
                                                      (conj sector sector-data))
                                              (reset! sector-selected? true))
                            :on-mouse-leave #(reset! sector-selected? false)
                            :on-click (fn [e]
                                        (reset! sector-info
                                                (conj sector sector-data))
                                        (reset! sector-selected? true))
                            :fill
                            (case zone-by
                              :pps (pps->color pps)
                              :freq (freq->color (/ shots (or total-shots 1)))
                              :pts (freq->color (/ pts (or total-pts 1)))
                              "red")}
               sector])))])
     (when (<sub [::subs/show-shots?])
       [:g
        (for [{:keys [game team action pts]} (<sub [::subs/shots])]
          ^{:key (gensym "key-")}
          (letfn [(select-shot [x y]
                    (fn [e]
                      (reset! selected? true)
                      (reset! shot-info
                              {:game game
                               :team team
                               :action action
                               :x x
                               :y y})))]
            [shot {:key (gensym "shot-")
                   :on-click select-shot
                   :on-mouse-enter select-shot
                   :on-mouse-leave #(reset! selected? false)}
             action pts]))])
     [shot-information selected? shot-info]
     [sector-information sector-selected? sector-info]]))


;; TODO - make this a reagent component,
;; set on-blur
;; set on debounce
(defn analysis-players-selector
  []
  [:input.rounded-full.border.border-black.px-2.py-1
   {:type "text"
    :placeholder "Player"
    :value (<sub [::subs/shot-chart-players-input])
    :on-change #(re-frame/dispatch [::events/set-shot-chart-players-input (-> % .-target .-value)])}])


(defn analysis-offense-selector
  []
  [:input.rounded-full.border.border-black.px-2.py-1
   {:type "text"
    :value (<sub [::subs/shot-chart-offense-input])
    :on-change #(re-frame/dispatch [::events/set-shot-chart-offense-input (-> % .-target .-value)])}])


(defn analysis-teams-selector
  []
  (let [teams          (sort-by :team/name (<sub [::subs/teams]))
        selections     (<sub [::subs/shot-chart-teams])]
    [:div
     [:p "Teams"]
     [selection-list
      :src (at)
      :height     "160px"
      :model          selections
      :choices        teams
      :label-fn       :team/name
      :id-fn :db/id
      :multi-select?  true
      :on-change      (fn [sel]
                        (re-frame/dispatch
                         [::events/set-shot-chart-teams sel]))]
     [:div.flex.gap-1.mt-1
      [button {:class "px-2"
               :on-click #(re-frame/dispatch [::events/set-shot-chart-teams #{}])} "Clear"]
      [button {:class "px-2 whitespace-nowrap"
               :on-click #(re-frame/dispatch [::events/set-shot-chart-teams (set (map :db/id teams))])} "Select All"]]]))


(defn analysis-games-selector
  []
  (let [games (sort-by :game/datetime (<sub [::subs/games]))
        selections (<sub [::subs/shot-chart-games])]
    [:div
     [:p "Games"]
     [selection-list
      :src (at)
           ;; :width          "391px"      ;; manual hack for width of variation panel A+B 1024px
      :height     "160px"       ;; based on compact style @ 19px x 5 rows
      :model          selections
      :choices        games
      :label-fn       (fn [{:game/keys [home-team away-team datetime]}]
                        (str (:team/name away-team) " at " (:team/name home-team)
                             ", " (when datetime
                                    (.toDateString datetime))))
      :id-fn :db/id
      :multi-select?  true
      :on-change      (fn [sel]
                        (re-frame/dispatch
                         [::events/set-shot-chart-games sel]))]
     [:div.flex.gap-1.mt-1
      [button {:class "px-2"
               :on-click #(re-frame/dispatch [::events/set-shot-chart-games #{}])}
       "Clear"]
      [button {:class "px-2"
               :on-click #(re-frame/dispatch [::events/set-shot-chart-games (into #{} (map :db/id games))])}
       "Select All"]]]))


(defn analysis-stats
  []
  (let [players-input? (seq (<sub [::subs/shot-chart-players-input]))
        {:keys [pts possessions pts-per-75 offrtg]
         :or {pts 0 possessions 0 offrtg 0 pts-per-75 0}} (if players-input?
                                                            (<sub [::subs/points-per-75])
                                                            (<sub [::subs/ppp]))
        ts% (or (<sub [::subs/true-shooting]) 0)
        ts-diff (- ts% (<sub [::subs/true-shooting-average]))
        offreb (or (<sub [::subs/offensive-rebounding-rate]) 0)
        turnover-rate (or (<sub [::subs/turnovers-per-play]) 0)
        ft-rate (or (<sub [::subs/free-throw-rate]) 0)
        efg% (or (<sub [::subs/effective-field-goal-percentage]) 0)
        usage% (or (<sub [::subs/usage-rate]) 0)
        three-fg% (or (<sub [::subs/three-fg-percentage]) 0)
        {pts-allowed :pts defensive-possessions :possessions defrtg :defrtg
         :or {pts-allowed 0 defensive-possessions 0 defrtg 0}} (<sub [::subs/defensive-ppp])]
    [:table.whitespace-nowrap
     [:thead
      [:tr.text-left
       [:th]
       [:th "Offense"]
       (when-not players-input?
         [:th "Defense"])]]
     [:tbody
      [:tr
       [:td "Points"]
       [:td pts]
       (when-not players-input?
         [:td pts-allowed])]
      [:tr
       [:td "Possessions"]
       [:td possessions]
       (when-not players-input?
         [:td defensive-possessions])]
      (if players-input?
        [:tr
         [:td "Points/75"]
         [:td (.toFixed pts-per-75 2)]]
        [:tr
         [:td "OffRtg"]
         [:td (.toFixed offrtg 2)]
         [:td (.toFixed defrtg 2)]])
      (when players-input?
        [:tr
         [:td "Usage%: "]
         [:td (.toFixed usage% 2)]])
      [:tr
       [:td "TS%"]
       [:td
        (.toFixed ts% 2)
        (when (pos? ts%)
          [:span
           " ("
           [:span {:class (cond
                            (zero? ts-diff) "text-slate-500"
                            (neg? ts-diff) "text-red-500"
                            (pos? ts-diff) "text-green-500")}
            (str (when-not (neg? ts-diff) "+") (.toFixed ts-diff 2) "%")]
           ")"])]]
      [:tr
       [:td "eFG%"]
       [:td (.toFixed efg% 2)]]
      [:tr
       [:td "3FG%"]
       [:td (.toFixed three-fg% 2)]]
      [:tr
       [:td "OffReb%"]
       [:td (.toFixed (* 100 offreb) 2)]]
      [:tr
       [:td "TO%"]
       [:td (.toFixed (* 100 turnover-rate) 2)]]
      [:tr
       [:td "FTRate"]
       [:td (.toFixed (* 100 ft-rate) 2)]]]]))


(defn analysis-chart-settings
  []
  (let [zones? (<sub [::subs/show-zones?])
        zone-layouts (<sub [::subs/zone-layouts])]
    [:div.flex.gap-1.flex-wrap
     [button
      {:class "px-2"
       :selected? (<sub [::subs/show-shots?])
       :on-click #(re-frame/dispatch [::events/toggle-show-shots?])}
      "Show shots?"]
     [button
      {:class "px-2"
       :selected? (<sub [::subs/show-zones?])
       :on-click #(re-frame/dispatch [::events/toggle-show-zones?])}
      "Show zones?"]
     (when zones?
       [:div.flex.gap-2
        [dropdown
         :choices [{:id :pps
                    :label "Points per shot"}
                   {:id :freq
                    :label "Shot frequency"}
                   {:id :pts
                    :label "Total points"}]
         :model (<sub [::subs/zone-by])
         :on-change (fn [id]
                      (re-frame/dispatch [::events/zone-by id]))]
        [dropdown
         :choices zone-layouts
         :model (<sub [::subs/zone-layout])
         :on-change (fn [zone-layout]
                      (re-frame/dispatch [::events/zone-layout zone-layout]))]])]))


(defn analysis
  []
  [:div.flex.flex-col.items-stretch.gap-2
   [analysis-chart-settings]
   [:div.flex.gap-2.flex-wrap.md:flex-nowrap.items-start
    [:div.md:order-last
     [analysis-stats]]
    [shot-chart]]
   [:div.self-start
    [analysis-players-selector]]
   [:div.flex.gap-2
    [analysis-teams-selector]
    [analysis-games-selector]]])
