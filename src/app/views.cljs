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


(defn period-handler
  [e]
  (let [checked (-> e .-target .-checked)]
    (re-frame/dispatch (if checked
                         [::events/next-period]
                         [::events/undo-next-period]))))


(defn stats []
  (let [[team1 team2] (<sub [::subs/teams])
        t1 (:db/id team1)
        t2 (:db/id team2)
        period (<sub [::subs/period])
        team1-score (<sub [::subs/team-score t1])
        team2-score (<sub [::subs/team-score t2])
        team1-preview-score (<sub [::subs/preview-team-score t1])
        team2-preview-score (<sub [::subs/preview-team-score t2])]
    [:div
     [:div.flex.justify-between
      [:h2.text-xl.border-y-4
       (:team/name team1)]
      [:label
       (period->string period 4 "Q")
       [:input.ml-2 {:type "checkbox"
                     :on-change period-handler
                     :checked (some? (<sub [::subs/init]))}]]
      [:h2.text-xl.border-y-4
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


;; TODO - think of a better way to have inputs here
;;        maybe keyword arguments?
(defn player-input
  [offense? player-sub player-event label required?]
  (let [player (re-frame/subscribe player-sub)
        players (<sub [(if offense? ::subs/offense-players ::subs/defense-players)])
        on-player-change (fn [e]
                           (let [p (-> e .-target .-value parse-long)]
                             (when p (re-frame/dispatch [player-event p]))))]
    [:label
     [:select.w-12 {:on-change on-player-change
                    :value (or @player "")
                    :required required?}
      (when-not @player [:option {:value ""} ""])
      (for [[i p] (zipmap (range) players)]
        [:option {:key i :value p} (str p)])]
     label]))


(defn rebound-input []
  (let [off-reb? (<sub [::subs/off-reb?])
        team-reb? (<sub [::subs/team-reb?])]
    [:div.flex.flex-col
     [:label
      [:input {:type "checkbox"
               :on-change #(re-frame/dispatch [::events/set-off-reb? (-> % .-target .-checked)])
               :checked (boolean off-reb?)}]
      "Off-Reb?"]
     [:label
      [:input {:type "checkbox"
               :on-change #(re-frame/dispatch [::events/set-team-reb? (-> % .-target .-checked)])
               :checked (boolean team-reb?)}]
      "Team Reb?"]
     (when-not team-reb?
       [player-input off-reb? [::subs/rebounder] ::events/set-rebounder "Rebounder"
        true ;; this isn't guaranteed to be true if after a foul shot
        ])]))


(defn ftm-input []
  (let [ftm (re-frame/subscribe [::subs/ft-made])
        fta (re-frame/subscribe [::subs/ft-attempted])]
    [:label
     [:input.w-12 {:type "number"
                   :on-change #(re-frame/dispatch [::events/set-ft-made (-> % .-target .-value parse-long)])
                   :value @ftm
                   :min 0
                   :max @fta
                   :required true}]
     (str "/ " @fta " FTs")]))


(defn foul-input []
  (let [value (re-frame/subscribe [::subs/shot-value])
        foul? (re-frame/subscribe [::subs/foul?])]
    [:div.flex.flex-col
     [:label
      [:input {:type "checkbox"
               :on-change #(re-frame/dispatch [::events/set-shot-foul? (-> % .-target .-checked)])
               :disabled (nil? @value)
               :checked (boolean @foul?)}]
      "Foul?"]
     (when @foul?
       [ftm-input])]))


(defn turnover-input []
  [player-input false [::subs/stealer] ::events/set-stealer "Stealer" false])


(defn bonus-input []
  (let [ftm (<sub [::subs/ft-made])
        fta (<sub [::subs/ft-attempted])]
    [:div.flex
     [:label
      [:input.w-12 {:type "number"
                    :on-change #(re-frame/dispatch [::events/set-ft-made (-> % .-target .-value parse-long)])
                    :value ftm
                    :min 0
                    :max fta
                    :required true}]
      "FTs"]
     [:label
      [:input.w-12 {:type "number"
                    :on-change #(re-frame/dispatch [::events/set-ft-attempted (-> % .-target .-value parse-long)])
                    :value fta
                    :min 1
                    :max 2
                    :required true}]
      "/FTAs"]]))


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
      [:input.w-8.h-8.text-center.border-green-500.bg-transparent.border.border-green-500.text-green-700.font-semibold.rounded-full
       {:class "cursor-pointer focus:cursor-text"
        :type "text"
        :value @player
        :on-blur on-blur
        :on-key-down on-key-down
        :on-change #(reset! player (-> % .-target .-value parse-long))
        :on-focus #(reset! player nil)}])))


(defn on-court-player-btn
  [player {:keys [selected? disabled? on-click]
           :or {selected? false disabled? false}}]
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


(defn players-input
  []
  [:div
   (doall
    (for [team (<sub [::subs/teams])]
      [:div.border.rounded.p-2.mb-2 {:key (:db/id team)}
       [:h2.mb-2 (:team/name team)]
       [:ul.flex.flex-col.gap-1
        (doall
         (for [player (sort (<sub [::subs/team-players-on-court (:db/id team)]))]
           [:li
            {:key player}
            (let [selected? (= player (<sub [::subs/action-player]))
                  disabled? (not= team (<sub [::subs/team]))]
              [:div.flex.items-center.justify-between.gap-1
               [on-court-player-btn player
                {:disabled? disabled?
                 :selected? selected?
                 :on-click #(re-frame/dispatch [::events/set-player player]) ;; This will change based on what player input needed, ala ::events/set-rebounder, etc.
                 }]
               [:button.border.border-transparent.hover:border-rose-500.text-rose-700.font-semibold.rounded-full
                {:class "px-1"
                 :type "button"
                 :on-click #(re-frame/dispatch [::events/put-player-to-bench (:db/id team) player])}
                [:span.inline-block.w-4.h-4 "-"]]])]))

        (for [player (sort (<sub [::subs/team-players-on-bench (:db/id team)]))]
          [:li
           {:key player}
           [:div.flex.items-center.justify-between.group.gap-1
            [:button.border-orange-500.hover:border-transparent.bg-transparent.border.border-orange-500.hover:bg-orange-500.text-orange-700.hover:text-white.font-semibold.rounded-full
             {:class "w-8 h-8"
              :type "button"
              :on-click #(re-frame/dispatch [::events/put-player-to-court (:db/id team) player])}
             (str player)]
            [:button.invisible.group-hover:visible.border.border-transparent.hover:border-rose-500.text-rose-700.font-semibold.rounded-full
             {:class "px-1"
              :type "button"
              :on-click #(re-frame/dispatch [::events/delete-bench-player (:db/id team) player])}
             [:span.inline-block.w-4.h-4 "⨯"]]]])
        [:li {:key -1}
         [add-player (:db/id team)]]]]))])


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


(defn team-selector
  []
  (let [[team1 team2] (<sub [::subs/teams])
        team (<sub [::subs/team])
        mid-period? (<sub [::subs/mid-period?])]
    [:div.flex
     [:label [:input {:type "radio"
                      :value team1
                      :name :team
                      :checked (= team team1)
                      :disabled mid-period?
                      :required true
                      :on-change #(when (-> % .-target .-checked) (re-frame/dispatch [::events/set-init-team team1]))}]
      (:team/name team1)]
     [:label.ml-2
      [:input {:type "radio"
               :value team2
               :name :team
               :checked (= team team2)
               :disabled mid-period?
               :required true
               :on-change #(when (-> % .-target .-checked) (re-frame/dispatch [::events/set-init-team team2]))}]
      (:team/name team2)]]))


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
  (let [scale 0.5
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
             icon-size 10]
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


(defn action-input []
  (let [type (re-frame/subscribe [::subs/action-type])]
    [:form {:on-submit submit-action-input}
     [:div.flex.flex-col
      [court]
      [:label
       [:input {:type "radio"
                :value :action.type/turnover
                :name :action/type
                :on-change #(when (-> % .-target .-checked) (re-frame/dispatch [::events/set-action-turnover]))
                :checked (= @type :action.type/turnover)}]
       "Turnover"]

      [:label
       [:input {:type "radio"
                :value :action.type/bonus
                :name :action/type
                :on-change #(when (-> % .-target .-checked) (re-frame/dispatch [::events/set-action-bonus]))
                :checked (= @type :action.type/bonus)}]
       "Bonus"]
      [:label
       [:input {:type "radio"
                :value :action.type/technical
                :name :action/type
                :on-change #(when (-> % .-target .-checked) (re-frame/dispatch [::events/set-action-technical]))
                :checked (= @type :action.type/technical)}]
       "Technical"]
      (when (= @type :action.type/shot)
        [foul-input])
      (when (= @type :action.type/turnover)
        [turnover-input])
      (when (= @type :action.type/bonus)
        [bonus-input])
      (when (= @type :action.type/technical)
        [ftm-input])
      (when (<sub [::subs/reboundable?])
        [rebound-input])
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
     [team-selector]
     [action-input]
     [new-game]]
    [:div.flex.flex-col.flex-1
     [stats]
     [possessions]]]])

