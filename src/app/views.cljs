(ns app.views
  (:require [app.events :as events]
            [app.subs :as subs]
            [cljs.math :as math]
            [clojure.string :as string]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]))


(def <sub  (comp deref re-frame/subscribe))


(defn stat
  [label display-fn query1 query2]
  [:div.flex
   [:code.flex-1 (some-> (<sub query1) display-fn)]
   [:p.flex-1.text-center label]
   [:code.flex-1.text-right (some-> (<sub query2) display-fn)]])


(defn period->string
  [period nperiods abbrev]
  (if (<= period nperiods)
    (str period abbrev)
    (str (- period nperiods) "OT")))


(defn period-button
  []
  (let [period (<sub [::subs/period])
        init? (some? (<sub [::subs/init]))
        on-click #(re-frame/dispatch
                   (if init?
                     [::events/undo-next-period]
                     [::events/next-period]))]
    [:button
     {:type "button"
      :class ["font-semibold rounded-full border border-blue-500 px-2 py-1"
              (if init?
                "bg-blue-500 text-white"
                "bg-transparent hover:bg-blue-500 text-blue-700 hover:text-white")]
      :on-click on-click}
     (period->string period 4 "Q")]))


(defn stats []
  (let [[team1 team2] (<sub [::subs/teams])
        t1 (:db/id team1)
        t2 (:db/id team2)
        team1-score (<sub [::subs/team-score t1])
        team2-score (<sub [::subs/team-score t2])
        team1-preview-score (<sub [::subs/preview-team-score t1])
        team2-preview-score (<sub [::subs/preview-team-score t2])]
    [:div
     [:div.flex.justify-between
      [:h2.text-xl
       (:team/name team1)]
      [period-button]
      [:h2.text-xl
       (:team/name team2)]]
     [:div.flex.justify-between
      [:div.flex.items-center.gap-2
       [:code.text-3xl.font-bold team1-score]
       (when (not= team1-score team1-preview-score)
         [:code.text-xl.text-blue-700 (str "+" (- team1-preview-score team1-score))])]
      [:div.flex.items-center.gap-2
       (when (not= team2-preview-score team2-score)
         [:code.text-xl.text-blue-700 (str "+" (- team2-preview-score team2-score))])
       [:code.text-3xl.font-bold team2-score]]]
     [stat "PPP" #(.toFixed % 2) [::subs/team-ppp t1] [::subs/team-ppp t2]]
     [stat "PPS" #(.toFixed % 2) [::subs/team-pps t1] [::subs/team-pps t2]]
     [stat "eFG%" #(-> % (* 100) .toFixed) [::subs/team-efg t1] [::subs/team-efg t2]]
     [stat "OffReb%" #(-> % (* 100) .toFixed) [::subs/team-off-reb-rate t1] [::subs/team-off-reb-rate t2]]
     [stat "TOV%" #(-> % (* 100) .toFixed) [::subs/team-turnover-rate t1] [::subs/team-turnover-rate t2]]
     [stat "FT/FGA" #(.toFixed % 2) [::subs/team-ft-rate t1] [::subs/team-ft-rate t2]]
     [stat "FT/Shot" #(.toFixed % 2) [::subs/team-fts-per-shot t1] [::subs/team-fts-per-shot t2]]]))


(defn foul?-button []
  (let [foul? (<sub [::subs/foul?])]
    [:button
     {:type "button"
      :class ["font-semibold rounded-full border border-blue-500 px-2 py-1"
              (if foul?
                "bg-blue-500 text-white"
                "bg-transparent hover:bg-blue-500 text-blue-700 hover:text-white")]
      :on-click #(re-frame/dispatch [::events/set-shot-foul? (not foul?)])}
     "Foul?"]))


(defn add-player
  [team-id]
  (let [player (reagent/atom "+")
        add-player #(re-frame/dispatch [::events/add-player team-id @player])
        valid? (fn [player] (and (number? player) (>= player 0) (<= player 99)))
        on-blur (fn [e]
                  (when (valid? @player)
                    (add-player))
                  (reset! player "+"))
        on-key-down (fn [e]
                      (when (and (= 13 (.-keyCode e)) (valid? @player))
                        (add-player)
                        (reset! player nil)))]
    (fn [team-id]
      [:input.w-8.h-8.text-center.bg-transparent.border.border-green-500.text-green-700.hover:text-white.hover:bg-green-500.font-semibold.rounded-full
       {:class "cursor-pointer focus:cursor-text"
        :type "text"
        :value @player
        :on-blur on-blur
        :on-key-down on-key-down
        :on-change #(reset! player (-> % .-target .-value parse-long))
        :on-focus #(reset! player nil)}])))


(defn on-court-player-btn
  [player {:keys [selected? disabled? on-click]}]
  [:button
   {:type "button"
    :on-click on-click
    :disabled (or selected? disabled?)
    :class ["font-semibold w-8 h-8 rounded-full"
            (if disabled?
              "bg-transparent text-blue-700 border border-blue-500 opacity-50 cursor-not-allowed"
              (if selected?
                "bg-blue-500 text-white"
                "bg-transparent hover:bg-blue-500 text-blue-700 hover:text-white border border-blue-500 hover:border-transparent"))]}
   (str player)])


(defn team-button
  [team {:keys [disabled? selected? on-click]}]
  [:button.mb-2
   {:type "button"
    :class ["font-semibold rounded-full border border-blue-500 px-2 py-1"
            (if disabled?
              "bg-transparent text-blue-700 opacity-50 cursor-not-allowed"
              (if selected?
                "bg-blue-500 text-white"
                "bg-transparent hover:bg-blue-500 text-blue-700 hover:text-white"))]
    :on-click on-click
    :disabled disabled?}
   [:h2 (:team/name team)]])


(defn players-input
  []
  (let [need-action-player? (<sub [::subs/need-action-player?])
        need-rebound-player? (<sub [::subs/need-rebound-player?])
        need-stealer-player? (<sub [::subs/need-stealer-player?])
        team-reb? (<sub [::subs/team-reb?])
        off-reb? (<sub [::subs/off-reb?])]
    [:div
     (doall
      (for [team (<sub [::subs/teams])]
        (let [offense? (= team (<sub [::subs/team]))]
          [:div.border.border-2.rounded-md.mb-2.p-2
           {:key (:db/id team)
            :class (if offense? "border-blue-500" "border-transparent")}
           (let [mid-period? (<sub [::subs/mid-period?])]
             [team-button team
              {:disabled? (and (not need-rebound-player?) mid-period?)
               :selected? (and team-reb? (= offense? off-reb?))
               :on-click (fn []
                           (if mid-period?
                             (do (re-frame/dispatch [::events/set-off-reb? offense?])
                                 (re-frame/dispatch [::events/set-team-reb? true]))
                             (re-frame/dispatch [::events/set-init-team team])))}])
           [:ul.flex.flex-col.gap-1
            (doall
             (for [player (<sub [::subs/team-players-on-court (:db/id team)])]
               [:li
                {:key player}
                (let [selected? (cond need-action-player? (= player (<sub [::subs/action-player]))
                                      need-rebound-player? (and (= off-reb? offense?)
                                                                (= player (<sub [::subs/rebounder])))
                                      need-stealer-player? (= player (<sub [::subs/stealer])))
                      disabled? (cond need-action-player? (not offense?)
                                      need-rebound-player? false
                                      need-stealer-player? offense?
                                      :else true)
                      on-click (cond need-action-player? #(re-frame/dispatch [::events/set-player player])
                                     need-rebound-player? (fn []
                                                            (re-frame/dispatch [::events/set-off-reb? offense?])
                                                            (re-frame/dispatch [::events/set-rebounder player]))
                                     need-stealer-player? #(re-frame/dispatch [::events/set-stealer player]))]
                  [:div.flex.items-center.justify-between.gap-1
                   [on-court-player-btn player
                    {:disabled? disabled?
                     :selected? selected?
                     :on-click on-click}]
                   [:button.border.border-transparent.hover:border-red-500.text-red-700.font-semibold.rounded-full
                    {:class "px-1"
                     :type "button"
                     :on-click #(re-frame/dispatch [::events/put-player-to-bench (:db/id team) player])}
                    [:span.inline-block.w-4.h-4 "-"]]])]))

            (for [player (<sub [::subs/team-players-on-bench (:db/id team)])]
              [:li
               {:key player}
               [:div.flex.items-center.justify-between.group.gap-1
                [:button.border-orange-500.hover:border-transparent.bg-transparent.border.border-orange-500.hover:bg-orange-500.text-orange-700.hover:text-white.font-semibold.rounded-full
                 {:class "w-8 h-8"
                  :type "button"
                  :on-click #(re-frame/dispatch [::events/put-player-to-court (:db/id team) player])}
                 (str player)]
                [:button.invisible.group-hover:visible.border.border-transparent.hover:border-red-500.text-red-700.font-semibold.rounded-full
                 {:class "px-1"
                  :type "button"
                  :on-click #(re-frame/dispatch [::events/delete-bench-player (:db/id team) player])}
                 [:span.inline-block.w-4.h-4 "⨯"]]]])
            [:li {:key -1}
             [add-player (:db/id team)]]]])))]))


(defn render-fts
  [ftm fta]
  [:span (str ftm "/" fta " FTs")])


(defn render-rebound
  [{rebounder :rebound/player off-reb? :rebound/off? team-reb? :rebound/team?}]
  [:span
   (str (when (some? rebounder) (str " #" rebounder))
        (when (true? team-reb?) " team")
        (if off-reb? " OffReb" " DefReb"))])


(defn render-shot
  [action]
  (let [{:shot/keys [make? value distance]
         ftm :ft/made fta :ft/attempted
         rebounder :rebound/player team-rebound? :rebound/team?} action
        distance-ft (math/round (/ distance 12))]
    [:span
     (str value " PT (" distance-ft "') " (if make? "make " "miss "))
     (when (and (some? fta) (> fta 0))
       [render-fts ftm fta])
     (when (or (some? rebounder) team-rebound?)
       [render-rebound action])]))


(defn render-turnover
  [action]
  (let [{stealer :steal/player} action]
    [:span (str "turnover" (when stealer (str " #" stealer " Steal")))]))


(defn render-bonus
  [action]
  (let [{ftm :ft/made fta :ft/attempted
         rebounder :rebound/player team-reb? :rebound/team?} action]
    [:span "bonus "
     [render-fts ftm fta]
     (when (or (some? rebounder) (true? team-reb?))
       [render-rebound action])]))


(defn render-technical
  [action]
  (let [{ftm :ft/made fta :ft/attempted} action]
    [:span "technical "
     [render-fts ftm fta]]))


(defn render-action
  [action & {:keys [preview-entities]}]
  (let [{:action/keys [player type] :keys [db/id]} action]
    [:div
     {:class (when (contains? preview-entities id)
               "text-blue-700")}
     (when (some? player)
       [:span (str "#" player " ")])
     (case type
       :action.type/shot [render-shot action]
       :action.type/turnover [render-turnover action]
       :action.type/bonus [render-bonus action]
       :action.type/technical [render-technical action]
       [:span type])]))


(defn render-possession
  [possession {:keys [preview-entities]}]
  (let [{actions :possession/action team :possession/team order :possession/order id :db/id} possession
        team (:team/name team)]
    [:div
     {:class (when (contains? preview-entities id) "text-blue-700")}
     [:p.font-bold (str order ". " team)]
     [:ul.ml-8
      (for [action actions]
        [:li
         {:key (:db/id action)}
         [render-action action {:preview-entities preview-entities}]])]]))


(defn possessions
  []
  (let [possessions (<sub [::subs/preview-possessions])
        preview-entities (<sub [::subs/preview-entities])]
    [:div.my-4
     [:h2.text-xl "Possessions"]
     [:ol.max-h-64.overflow-auto.flex.flex-col-reverse
      (for [possession possessions]
        [:li
         {:key (:db/id possession)}
         [render-possession possession {:preview-entities preview-entities}]])]]))


(defn court-bounding-rect-xy
  [court-id]
  (let [bounding-client-rect (.getBoundingClientRect (.getElementById js/document court-id))]
    [(.-left bounding-client-rect)
     (.-top bounding-client-rect)]))


(defn polar-hoop->eucl-court
  [[hoop-x hoop-y] [turns radius]]
  (let [radians (* turns 2 math/PI)]
    [(-> radians math/sin (* radius) (+ hoop-x))
     (-> radians math/cos (* radius) (+ hoop-y))]))


(defn eucl-hoop->polar-hoop
  [[x y]]
  (let [turns (/ (math/atan2 x y) 2 math/PI)
        radius (math/sqrt (+ (* x x) (* y y)))]
    [turns radius]))


(defn eucl-court->eucl-hoop
  [[hoop-x hoop-y] [x y]]
  [(- x hoop-x)
   (- y hoop-y)])


(defn client->eucl-court
  [court-id [court-client-width court-client-height] [court-width court-height] [x y]]
  (let [[rect-x rect-y] (court-bounding-rect-xy court-id)]
    [(-> x (- rect-x) (/ court-client-width) (* court-width))
     (-> y (- rect-y) (/ court-client-height) (* court-height))]))

(defn client->polar-hoop
  [court-id court-client-dimensions court-dimensions hoop-coordinates [x y]]
  (->> [x y]
       (client->eucl-court court-id court-client-dimensions court-dimensions)
       (eucl-court->eucl-hoop hoop-coordinates)
       (eucl-hoop->polar-hoop)))


(defn svg-click-handler
  [court-id court-client-dimensions court-dimensions hoop-coordinates make?]
  (fn [e]
    (let [[turns radius] (client->polar-hoop
                          court-id court-client-dimensions court-dimensions hoop-coordinates
                          [(.-clientX e) (.-clientY e)])]
      (re-frame/dispatch [::events/set-action-shot-with-info turns (math/round radius) make?]))))


(defn svg-right-click-handler
  [court-id court-client-dimensions court-dimensions hoop-coordinates]
  (fn [e]
    (.preventDefault e)
    ((svg-click-handler court-id court-client-dimensions court-dimensions hoop-coordinates true) e)))


(defn svg-shot-miss
  [attrs]
  (let [{:keys [cx cy width height]} attrs
        x (- cx (/ width 2))
        y (- cy (/ height 2))
        d [\M x y \l width height \m (- width) 0 \l width (- height)]]
    [:path
     (-> attrs
         (dissoc :cx :cy :width :height)
         (assoc :d (string/join " " d)))]))


(defn court []
  (let [scale 0.8
        [court-width court-height :as court-dimensions] [(* 12 50) (* 12 42)]
        court-client-dimensions [(* scale court-width) (* scale court-height)]
        hoop-coordinates [(/ court-width 2) 63]
        shot-location (<sub [::subs/shot-location])
        line-width 2
        [svg-width svg-height] (map #(* scale (+ % (* 2 line-width))) court-dimensions)
        court-id "court"]
    [:svg
     {:xmlns "http://www.w3.org/2000/svg"
      :version "1.1"
      :width svg-width
      :height svg-height
      :view-box (string/join " " [(- line-width) (- line-width) (+ court-width (* 2 line-width)) (+ court-height (* 2 line-width))])
      :on-context-menu (svg-right-click-handler court-id court-client-dimensions court-dimensions hoop-coordinates)
      :on-click (svg-click-handler court-id court-client-dimensions court-dimensions hoop-coordinates false)}
     [:rect {:id court-id :x 0 :y 0 :width court-width :height court-height :fill (or "none" "#dfbb85")}]
     [:g {:id "lines" :fill "none" :stroke-width line-width :stroke "black" :stroke-linecap "butt" :stroke-linejoin "miter"}
      [:rect {:x (- (/ line-width 2)) :y (- (/ line-width 2))
              :width (+ line-width court-width) :height (+ line-width court-height)}]
      [:path {:d "M 63 0 L 63 63 a 237 237 0 0 0 474 0 l 0 -63"}] ;; 3 point arc
      [:circle {:cx 300 :cy 63 :r 9}] ;; hoop
      [:line {:x1 269 :y1 48 :x2 331 :y2 48}] ;; backboard
      [:polyline {:points "228 0 228 228 370 228 370 0"}] ;; paint
      [:path {:d "M 228 228 A 71 71 0 0 0 370 228"}] ;; top of key
      [:path {:d "M 228 504 a 71 71 0 0 1 142 0"}] ;; halfcourt circle
      [:path {:d "M 227 94 l -8 0 m 8 36 l -8 0 m 8 36 l -8 0 m 8 36 l -8 0"}] ; left lane markings
      [:path {:d "M 371 94 l 8 0 m -8 36 l 8 0 m -8 36 l 8 0 m -8 36 l 8 0"}] ; right lane markings
      ]
     (when (some? shot-location)
       (let [[x y] (polar-hoop->eucl-court hoop-coordinates shot-location)
             icon-size 8]
         (if (<sub [::subs/shot-make?])
           [:circle
            {:r icon-size :cx x :cy y
             :fill "none"
             :stroke "green"
             :stroke-width 2}]
           [svg-shot-miss (let [length (* 2 (/ icon-size (math/sqrt 2)))]
                            {:cx x :cy y :width length :height length
                             :stroke "red" :stroke-width 2})])))]))


(defn submit-action-input
  [e]
  (.preventDefault e)
  (re-frame/dispatch [::events/add-action]))


(defn action-type-button
  [{:keys [label type on-click]}]
  (let [selected? (= type (<sub [::subs/action-type]))]
    [:button
     {:type "button"
      :class ["font-semibold rounded-full border border-blue-500 px-2 py-1"
              (if selected?
                "bg-blue-500 text-white"
                "bg-transparent hover:bg-blue-500 text-blue-700 hover:text-white")]
      :on-click on-click}
     label]))


(defn action-input []
  (let [type (re-frame/subscribe [::subs/action-type])]
    [:form {:on-submit submit-action-input}
     [:div.flex.flex-col.items-start
      [court]
      [:div.my-2.flex.gap-2
       [action-type-button
        {:label "Turnover"
         :type :action.type/turnover
         :on-click #(re-frame/dispatch [::events/set-action-turnover])}]
       [action-type-button
        {:label "Bonus"
         :type :action.type/bonus
         :on-click #(re-frame/dispatch [::events/set-action-bonus])}]
       [action-type-button
        {:label "Technical"
         :type :action.type/technical
         :on-click #(re-frame/dispatch [::events/set-action-technical])}]]
      [:div.flex.gap-2
       (when (= @type :action.type/shot)
         [foul?-button])
       [:ul.flex.gap-1
        (for [[idx make?] (zipmap (range) (<sub [::subs/ft-results]))]
          [:li {:key idx}
           [:button
            {:type "button"
             :class ["font-semibold rounded-full border border-blue-500 px-2 py-1"
                     (if make?
                       "bg-blue-500 text-white"
                       "bg-transparent hover:bg-blue-500 text-blue-700 hover:text-white")]
             :on-click #(re-frame/dispatch [::events/set-ft-result idx (not make?)])}
            "Make?"]])]
       (when (= @type :action.type/bonus)
         (let [fta (<sub [::subs/ft-attempted])
               add-ft? (= fta 1)]
           [:button.w-8.h-8.border.bg-transparent.font-semibold.rounded-full.hover:text-white
            {:type "button"
             :class (if add-ft?
                      "border-green-500 text-green-700 hover:bg-green-500"
                      "border-red-500 text-red-700 hover:bg-red-500")
             :on-click #(re-frame/dispatch
                         (if add-ft?
                           [::events/add-ft-attempted]
                           [::events/pop-ft-attempted]))}
            (if add-ft? "+" "-")]))]
      [:div.my-2
       [:button.self-start.bg-orange-500.hover:bg-orange-700.text-white.font-bold.py-1.px-2.rounded-full
        {:type "button"
         :on-click #(re-frame/dispatch [::events/undo-last-action])}
        "Undo"]
       [:button.self-start.ml-2.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-1.px-2.rounded-full
        {:type "submit"}
        "Add"]]]]))


(defn new-game
  []
  [:button.self-start.mt-8.bg-red-500.hover:bg-red-700.text-white.font-bold.py-1.px-2.rounded-full
   {:type "button"
    :on-click #(re-frame/dispatch [::events/new-game])}
   "New Game"])


(defn main-panel []
  [:div
   [:div.container.mx-4.my-4.flex.justify-between.gap-4
    {:class "w-11/12"}
    [players-input]
    [:div.flex.flex-col.flex-1
     [action-input]
     [new-game]]
    [:div.flex.flex-col.flex-1
     [stats]
     [possessions]]]])

