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


(defn pallete->color
  [pallete x]
  (get pallete (->> (keys pallete)
                    (filter #(< x %))
                    (reduce min))
       (second (reduce max pallete))))


(def orange-purple-divergent
  ["#dd7d11"
   "#e5903f"
   "#eca363"
   "#f1b686"
   "#f3c9a9"
   "#f3ddcc"
   "#dcd8ee"
   "#c8c0ec"
   "#b2a8e9"
   "#9b91e5"
   "#827be2"
   "#6665de"])


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
     (zipmap (let [step 0.015]
               (iterate #(+ % step) step))
             purple-single)

     percent)))


(defn shot-information
  [hovered? shot-info]
  (when @hovered?
    (let [{:keys [game team action x y]} @shot-info
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
  [{:keys [on-mouse-enter on-mouse-leave]} action pts]
  (let [{:shot/keys [angle distance]} action
        [x y] (polar-hoop->eucl-court hoop-coordinates [angle distance])
        icon-size 4
        color (get {0 "#c0c0c0" #_"cornsilk"
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
       :on-mouse-leave on-mouse-leave}]]))


(defn shot-chart
  []
  (let [hovered? (reagent/atom false)
        shot-info (reagent/atom {})
        sector-info (reagent/atom {})
        sector-hovered? (reagent/atom false)
        {total-pts :pts total-shots :shots} (<sub [::subs/shot-data])
        zone-by (<sub [::subs/zone-by])
        shot-data-by-sector (<sub [::subs/shot-data-by-sector])]
    [court
     {:id "shot-chart"
      :scale 0.8}
     (when (<sub [::subs/show-zones?])
       [:g {:stroke-width 0.5 :stroke "black"}
        (let [sector-pps-map (->> shot-data-by-sector
                                  (map (juxt :sector #(dissoc % :sector)))
                                  (into {}))]
          (for [{:keys [min-distance max-distance] :as sector} (sort-by :max-distance > (<sub [::subs/sectors]))]
            (let [{:keys [pps shots pts]
                   :or {shots 0 pts 0}
                   :as sector-data} (get sector-pps-map sector)]
              ^{:key (gensym "key-")}
              [draw-sector {:on-mouse-enter (fn [e]
                                              (reset! sector-info
                                                      (conj sector sector-data))
                                              (reset! sector-hovered? true))
                            :on-mouse-leave #(reset! sector-hovered? false)
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
          [shot {:on-mouse-enter (fn [x y]
                                   (fn [e]
                                     (reset! shot-info
                                             {:game game
                                              :team team
                                              :action action
                                              :x x
                                              :y y})
                                     (reset! hovered? true)))
                 :on-mouse-leave #(reset! hovered? false)}
           action pts])])
     [shot-information hovered? shot-info]
     [sector-information sector-hovered? sector-info]]))


;; TODO - make this a reagent component,
;; set on-blur
;; set on debounce
(defn analysis-players-selector
  []
  [:input.rounded-full.border.border-black.px-2.py-1
   {:type "text"
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
     [button {:class "px-2 py-1"
              :on-click #(re-frame/dispatch [::events/set-shot-chart-teams #{}])} "Clear All"]
     [button {:class "px-2 py-1"
              :on-click #(re-frame/dispatch [::events/set-shot-chart-teams (set (map :db/id teams))])} "Select All"]]))


(defn analysis-games-selector
  []
  (let [games (sort-by :game/datetime (<sub [::subs/games]))
        selections (<sub [::subs/shot-chart-games])]
    [:div
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
     [button {:class "px-2 py-1"
              :on-click #(re-frame/dispatch [::events/set-shot-chart-games #{}])} "Clear All"]]))


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
        usage% (or (<sub [::subs/usage-rate]) 0)]
    [:div.flex.justify-between.gap-4
     [:div
      [:p (str "Points: " pts)]
      [:p (str "Possessions: " possessions)]
      (if players-input?
        [:p (str "Points/75: " (.toFixed pts-per-75 2))]
        [:p (str "OffRtg: " (.toFixed offrtg 2))])
      (when players-input?
        [:p (str "Usage%: " (.toFixed usage% 2))])
      [:p (str "TS%: " (.toFixed ts% 2))
       (when (pos? ts%)
         [:span
          " ("
          [:span {:class (cond
                           (zero? ts-diff) "text-slate-500"
                           (neg? ts-diff) "text-red-500"
                           (pos? ts-diff) "text-green-500")}
           (str (when-not (neg? ts-diff) "+") (.toFixed ts-diff 2) "%")]
          ")"])]
      [:p (str "eFG%: " (.toFixed efg% 2))]
      [:p (str "OffReb%: " (.toFixed (* 100 offreb) 2))]
      [:p (str "TO%: " (.toFixed (* 100 turnover-rate) 2))]
      [:p (str "FTRate: " (.toFixed (* 100 ft-rate) 2))]]
     (when-not players-input?
       (let [{pts-allowed :pts defensive-possessions :possessions defrtg :defrtg
              :or {pts-allowed 0 defensive-possessions 0 defrtg 0}} (<sub [::subs/defensive-ppp])]
         [:div
          [:p (str "Points: " pts-allowed)]
          [:p (str "Possessions: " defensive-possessions)]
          [:p (str "DefRtg: " (.toFixed defrtg 2))]]))]))


(defn analysis-chart-settings
  []
  [:div.flex.gap-2
   [button
    {:class "px-2 py-1"
     :selected? (<sub [::subs/show-shots?])
     :on-click #(re-frame/dispatch [::events/toggle-show-shots?])}
    "Show shots?"]
   [button
    {:class "px-2 py-1"
     :selected? (<sub [::subs/show-zones?])
     :on-click #(re-frame/dispatch [::events/toggle-show-zones?])}
    "Show zones?"]
   (when (<sub [::subs/show-zones?])
     [dropdown
      :choices [{:id :pps
                 :label "Points per shot"}
                {:id :freq
                 :label "Shot frequency"}
                {:id :pts
                 :label "Total points"}]
      :model (<sub [::subs/zone-by])
      :on-change (fn [id]
                   (re-frame/dispatch [::events/zone-by id]))])])


(defn analysis
  []
  [:div.flex.flex-col.items-start.gap-2
   [analysis-chart-settings]
   [:div.flex.gap-2
    [shot-chart]
    [analysis-stats]]
   [:div.flex.gap-2
    [analysis-players-selector]
    [analysis-offense-selector]]
   [:div.flex
    [analysis-teams-selector]
    [analysis-games-selector]]])
