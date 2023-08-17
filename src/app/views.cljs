(ns app.views
  (:require
   [app.events :as events]
   [app.subs :as subs]
   [cljs.math :as math]
   [clojure.string :as string]
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [re-com.core :refer [at selection-list]]))


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


(defn button
  [{:keys [disabled? selected? on-click class]} label]
  [:button.font-semibold.rounded-full.border.border-blue-500
   {:type "button"
    :on-click on-click
    :disabled disabled?
    :class [class
            (cond
              selected? "bg-blue-500 text-white"
              disabled? "bg-transparent text-blue-700 opacity-50 cursor-not-allowed"
              :else "bg-transparent text-blue-700 hover:bg-blue-500 hover:text-white hover:border-transparent")]}
   label])


(defn period-button
  []
  (let [period (<sub [::subs/period])
        init? (some? (<sub [::subs/init]))
        on-click #(re-frame/dispatch
                   (if init?
                     [::events/undo-next-period]
                     [::events/next-period]))]
    [button
     {:class "px-2 py-1"
      :on-click on-click
      :selected? init?
      :disabled? (and init? (= period 1))}
     (period->string period 4 "Q")]))


(defn stats []
  (let [[team1 team2] (<sub [::subs/game-teams])
        t1 (:db/id team1)
        t2 (:db/id team2)
        team1-score (<sub [::subs/team-score t1])
        team2-score (<sub [::subs/team-score t2])
        team1-preview-score (<sub [::subs/preview-team-score t1])
        team2-preview-score (<sub [::subs/preview-team-score t2])]
    [:div
     ;; TODO - refactor this into "score"
     [:div.flex.justify-between.items-center
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


(defn team-edit
  [team]
  (let [team-name (reagent/atom (:team/name team))]
    (fn [team]
      [:input.rounded-full.border.border-green-500.text-green-700.px-2.py-1.font-mono
       {:type "text"
        :value @team-name
        :on-change #(reset! team-name (-> % .-target .-value))
        :on-blur #(when (not= @team-name (:team/name team))
                    ;; TODO - update-team-name... hmm.
                    ;; because sometimes you're setting the active team to a team that already
                    ;; exists in the local db. So you want the game/home-team to change in that case
                    ;; not just update :team/name
                    (re-frame/dispatch [::events/update-team-name (:db/id team) @team-name]))
        :style {:width (str (+ 2 (count @team-name)) "ch")}}])))


(defn team-button
  [team]
  (let [offense? (= team (<sub [::subs/team]))
        need-action-player? (<sub [::subs/need-action-player?])
        need-rebound-player? (<sub [::subs/need-rebound-player?])
        team-reb? (<sub [::subs/team-reb?])
        off-reb? (<sub [::subs/off-reb?])
        mid-period? (<sub [::subs/mid-period?])
        type? (nil? (<sub [::subs/action-type]))
        team-disabled? (if mid-period?
                         (not need-rebound-player?)
                         (and (not need-rebound-player?)
                              (not type?)
                              (not need-action-player?)))
        sub? (<sub [::subs/sub?])]
    (if sub?
      [:div.w-fit [team-edit team]]
      [button
       {:class "px-2 py-1"
        :disabled? team-disabled?
        :selected? (and team-reb? (= offense? off-reb?))
        :on-click (fn []
                    (if need-rebound-player?
                      (re-frame/dispatch [::events/set-team-reb? offense? true])
                      (re-frame/dispatch [::events/set-init-team team])))}
       (:team/name team)])))


(defn offensive-team-players
  [team]
  (let [{team-id :db/id} team
        players (<sub [::subs/team-players-on-court team-id])
        ft-players (<sub [::subs/team-players-on-court-ft team-id])
        sub? (<sub [::subs/sub?])
        reboundable? (<sub [::subs/reboundable?])
        rebounder (<sub [::subs/rebounder])
        ft? (<sub [::subs/ft?])
        off-reb? (<sub [::subs/off-reb?])
        rebounder? (fn [player] (and off-reb? (= player rebounder)))
        action-player (<sub [::subs/action-player])]
    [:div.flex.items-start.gap-1
     [:ul.flex.flex-col.gap-1
      (for [player players]
        [:li {:key (str team-id "#" player "primary")}
         [button
          {:selected? (= player action-player)
           :disabled? false
           :on-click (if sub?
                       #(re-frame/dispatch [::events/put-player-to-bench team-id player])
                       #(re-frame/dispatch [::events/set-player player]))
           :class "w-8 h-8"}
          (str player)]])]
     (when (if sub? ft? (or ft? reboundable?))
       [:ul.flex.flex-col.gap-1
        (for [player (if ft? ft-players players)]
          [:li {:key (str team-id "#" player "secondary")}
           [button
            {:selected? (cond reboundable? (rebounder? player)
                              :else false)
             :disabled? (if sub? (= player action-player) (not reboundable?))
             :on-click (cond (and sub? ft?) #(re-frame/dispatch [::events/put-player-to-ft-bench team-id player])
                             reboundable? #(re-frame/dispatch [::events/set-rebounder true player]))
             :class "w-8 h-8"}
            (str player)]])])]))


(defn defensive-team-players
  [team]
  (let [{team-id :db/id} team
        players (<sub [::subs/team-players-on-court team-id])
        ft-players (<sub [::subs/team-players-on-court-ft team-id])
        sub? (<sub [::subs/sub?])
        reboundable? (<sub [::subs/reboundable?])
        stealable? (= :action.type/turnover (<sub [::subs/action-type]))
        rebounder (<sub [::subs/rebounder])
        stealer (<sub [::subs/stealer])
        ft? (<sub [::subs/ft?])
        off-reb? (<sub [::subs/off-reb?])
        rebounder? (fn [player] (and (not off-reb?) (= player rebounder)))]
    [:div.flex.items-start.gap-1
     (when (or sub? ft? (and (not reboundable?) (not stealable?)))
       [:ul.flex.flex-col.gap-1
        (for [player players]
          [:li {:key (str team-id "#" player "primary")}
           [button
            {:selected? false
             :disabled? (not sub?)
             :on-click (when sub? #(re-frame/dispatch [::events/put-player-to-bench (:db/id team) player]))
             :class "w-8 h-8"}
            (str player)]])])
     (when (if sub? ft? (or ft? reboundable? stealable?))
       [:ul.flex.flex-col.gap-1
        (for [player (if ft? ft-players players)]
          [:li {:key (str team-id "#" player "secondary")}
           [button
            {:selected? (cond reboundable? (rebounder? player)
                              stealable? (= player stealer)
                              :else false)
             :disabled? (if sub? (not ft?) (and (not reboundable?) (not stealable?)))
             :on-click (cond (and sub? ft?) #(re-frame/dispatch [::events/put-player-to-ft-bench team-id player])
                             reboundable? #(re-frame/dispatch [::events/set-rebounder false player])
                             stealable? #(re-frame/dispatch [::events/set-stealer player]))
             :class "w-8 h-8"}
            (str player)]])])]))


(defn team-players-input
  [team]
  (let [offense? (= team (<sub [::subs/team]))
        team-id (:db/id team)
        bench-players (<sub [::subs/team-players-on-bench team-id])
        ft-bench-players (<sub [::subs/team-players-on-bench-ft team-id])
        sub? (<sub [::subs/sub?])
        ft? (<sub [::subs/ft?])]
    [:div.border.border-2.rounded-md.p-2.flex.flex-col.gap-2
     {:class (if offense? "border-blue-500" "border-transparent")}
     [team-button team]
     (if offense?
       [offensive-team-players team]
       [defensive-team-players team])
     (when sub?
       [:div.flex.gap-1
        [:ul.flex.flex-col.gap-1
         (for [player bench-players] ;; TODO - adjust this with (apply disj bench-players )
           [:li {:key (str team-id "#" player)}
            [:button.bg-transparent.border.border-orange-500.text-orange-700.font-semibold.rounded-full
             {:class ["w-8 h-8"
                      (if (not sub?)
                        "opacity-50 cursor-not-allowed"
                        "hover:bg-orange-500 hover:text-white")]
              :type "button"
              :disabled (not sub?)
              :on-click #(re-frame/dispatch [::events/put-player-to-court (:db/id team) player])}
             (str player)]])
         [:li {:key (str team-id "new-player")}
          [add-player team-id]]]
        (when ft?
          [:ul.flex.flex-col.gap-1
           (for [player ft-bench-players]
             [:li {:key (str team-id "#" player)}
              [:button.bg-transparent.border.border-orange-500.text-orange-700.font-semibold.rounded-full
               {:class ["w-8 h-8"
                        (if (not sub?)
                          "opacity-50 cursor-not-allowed"
                          "hover:bg-orange-500 hover:text-white")]
                :type "button"
                :disabled (not sub?)
                :on-click #(re-frame/dispatch [::events/put-player-to-ft-court (:db/id team) player])}
               (str player)]])])])]))


(defn players-input
  []
  (let [[team1 team2] (<sub [::subs/game-teams])
        sub? (<sub [::subs/sub?])]
    [:div.flex.flex-col.gap-2
     [team-players-input team1]
     [button {:class "px-2 py-1"
              :disabled? false
              :selected? sub?
              :on-click #(re-frame/dispatch [::events/toggle-sub?])}
      "Sub"]
     [team-players-input team2]]))


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
        distance-ft (math/floor (/ distance 12))]
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
    [:div
     [:h2.text-xl "Possessions"]
     [:ol.max-h-32.overflow-auto.flex.flex-col
      (for [possession possessions]
        [:li
         {:key (:db/id possession)}
         [render-possession possession {:preview-entities preview-entities}]])]]))


(defn bounding-rect-xy
  [id]
  (let [bounding-client-rect (.getBoundingClientRect (.getElementById js/document id))]
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
  (let [[rect-x rect-y] (bounding-rect-xy court-id)]
    [(-> x (- rect-x) (/ court-client-width) (* court-width))
     (-> y (- rect-y) (/ court-client-height) (* court-height))]))

(defn client->polar-hoop
  [court-id court-client-dimensions court-dimensions hoop-coordinates [x y]]
  (->> [x y]
       (client->eucl-court court-id court-client-dimensions court-dimensions)
       (eucl-court->eucl-hoop hoop-coordinates)
       (eucl-hoop->polar-hoop)))


(def court-dimensions [(* 12 50) (* 12 42)])


(def hoop-coordinates
  (let [[court-width _] court-dimensions]
    [(/ court-width 2) 63]))


(defn svg-click-handler
  [court-id scale make?]
  (let [court-client-dimensions (map #(* % scale) court-dimensions)]
    (fn [e]
      (let [[turns radius] (client->polar-hoop
                            court-id court-client-dimensions court-dimensions hoop-coordinates
                            [(.-clientX e) (.-clientY e)])]
        (re-frame/dispatch [::events/set-action-shot-with-info turns (math/round radius) make?])))))


(defn svg-right-click-handler
  [court-id scale]
  (fn [e]
    (.preventDefault e)
    ((svg-click-handler court-id scale true) e)))


(defn court [{:keys [scale id on-click on-context-menu]
              :or {scale 1 id (gensym "court-")}} & children]
  (let [[court-width court-height] court-dimensions
        line-width 2
        [svg-width svg-height] (map #(* scale (+ % (* 2 line-width))) court-dimensions)]
    [:svg
     {:xmlns "http://www.w3.org/2000/svg"
      :version "1.1"
      :width svg-width
      :height svg-height
      :view-box (string/join " " [(- line-width) (- line-width) (+ court-width (* 2 line-width)) (+ court-height (* 2 line-width))])
      :on-click on-click
      :on-context-menu on-context-menu}
     (for [child children]
       (with-meta child {:key (gensym "key-")}))
     [:rect {:id id :x 0 :y 0 :width court-width :height court-height :fill (or "none" "#dfbb85")}]
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
      ]]))


(defn action-input []
  (let [type (re-frame/subscribe [::subs/action-type])]
    [:form {:on-submit (fn submit-action-input
                         [e]
                         (.preventDefault e)
                         (re-frame/dispatch [::events/add-action]))}
     [:div.flex.flex-col.items-start.gap-2
      (let [shot-location (<sub [::subs/shot-location])
            id "court"
            scale 0.6]
        [court
         {:id id
          :scale scale
          :on-context-menu (svg-right-click-handler id scale)
          :on-click (svg-click-handler id scale false)}
         (when (some? shot-location)
           (let [[x y] (polar-hoop->eucl-court hoop-coordinates shot-location)
                 icon-size 5]
             [:circle
              {:r icon-size :cx x :cy y
               :fill "none"
               :stroke (if (<sub [::subs/shot-make?]) "green" "red")
               :stroke-width 2}]))])
      [:div.flex.gap-2
       [button
        {:class "px-2 py-1"
         :selected? (= @type :action.type/turnover)
         :on-click #(re-frame/dispatch [::events/set-action-turnover])}
        "Turnover"]
       [button
        {:class "px-2 py-1"
         :selected? (= @type :action.type/bonus)
         :on-click #(re-frame/dispatch [::events/set-action-bonus])}
        "Bonus"]
       [button
        {:class "px-2 py-1"
         :selected? (= @type :action.type/technical)
         :on-click #(re-frame/dispatch [::events/set-action-technical])}
        "Technical"]]
      [:div.flex.gap-2
       (when (= @type :action.type/shot)
         [:div.flex.gap-2
          (let [make? (<sub [::subs/shot-make?])]
            [button
             {:class "px-2 py-1"
              :selected? make?
              :on-click #(re-frame/dispatch [::events/set-shot-make? (not make?)])}
             "Make?"])
          (let [foul? (<sub [::subs/foul?])]
            [button
             {:class "px-2 py-1"
              :selected? foul?
              :on-click #(re-frame/dispatch [::events/set-shot-foul? (not foul?)])}
             "Foul?"])])
       [:ul.flex.gap-1
        (for [[idx make?] (zipmap (range) (<sub [::subs/ft-results]))]
          [:li {:key idx}
           [button
            {:class "px-2 py-1"
             :selected? make?
             :on-click #(re-frame/dispatch [::events/set-ft-result idx (not make?)])}
            "Make?"]])]
       (when (contains? #{:action.type/bonus :action.type/technical} @type)
         (let [add-ft? (<= (<sub [::subs/ft-attempted]) 1)]
           [button
            {:class "w-8 h-8"
             :on-click (if add-ft?
                         #(re-frame/dispatch [::events/add-ft-attempted])
                         #(re-frame/dispatch [::events/pop-ft-attempted]))}
            (if add-ft? "+" "-")]))]
      [:div.flex.gap-2
       (let [disabled? (and (nil? (<sub [::subs/action])) (not (<sub [::subs/possessions?])))]
         [:button.bg-orange-500.hover:bg-orange-700.text-white.font-bold.py-1.px-2.rounded-full
          {:type "button"
           :class (when disabled? "opacity-50 cursor-not-allowed")
           :disabled disabled?
           :on-click #(re-frame/dispatch [::events/undo-last-action])}
          "Undo"])
       (let [disabled? (not (<sub [::subs/valid?]))]
         [:button.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-1.px-2.rounded-full
          {:type "submit"
           :class (when disabled? "opacity-50 cursor-not-allowed")
           :disabled disabled?}
          "Add"])]]]))


(defn game-selector
  []
  [:select
   {:on-change #(re-frame/dispatch [::events/set-game (-> % .-target .-value parse-long)])
    :value (or (<sub [::subs/game-id]) "")}
   (for [game-id (<sub [::subs/game-ids])]
     [:option
      {:key game-id
       :value game-id}
      (str game-id)])])


(defn new-game
  []
  [:button.bg-red-500.hover:bg-red-700.text-white.font-bold.py-1.px-2.rounded-full
   {:type "button"
    :on-click #(re-frame/dispatch [::events/start-new-game])}
   "New Game"])


(defn submit-game
  []
  [:button.self-start.bg-purple-700.text-white.font-bold.py-1.px-2.rounded-full
   {:type "button"
    :on-click #(re-frame/dispatch [::events/submit-game])}
   "Submit"])


(defn game-input
  []
  [:div.flex.justify-between.gap-4
   [players-input]
   [action-input]])


(defn load-remote-games
  []
  (doseq [game events/blaine-games]
    (re-frame/dispatch [::events/load-remote-game game])))


(defn game-controls
  []
  [:div.flex.gap-2
   [new-game]
   [submit-game]
   [:button {:type "button"
             :on-click load-remote-games}
    "Add Remote Games"]])


(defn game-stats
  []
  [:div.flex.flex-col.flex-1.gap-4
   [stats]
   [possessions]])


(defn shot
  [{:keys [on-mouse-enter on-mouse-leave]} action pts]
  (let [{:shot/keys [angle distance]} action
        [x y] (polar-hoop->eucl-court hoop-coordinates [angle distance])
        icon-size 5
        color (get {0 #_"#c0c0c0" "cornsilk"
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
       :stroke-width 2
       :on-mouse-enter (on-mouse-enter x y)
       :on-mouse-leave on-mouse-leave}]]))


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
        (second (string/split (.toDateString datetime) #" " 2))]
       [:tspan
        {:x x :dx (if right-side? -15 15) :y y :dy 40}
        (str (quot (:shot/distance action) 12) "'" (rem (:shot/distance action) 12)  "\", " (.toFixed (:shot/angle action) 4) "tr")]])))


(defn sector-information
  [sector-hovered? sector-info]
  (when @sector-hovered?
    (let [{:keys [pps shots]
           :or {pps ##NaN shots 0}} @sector-info
          [_ court-y] court-dimensions]
      [:text
       [:tspan
        {:x 10 :y (- court-y 30)}
        (str "PPS: " (.toFixed pps 2))]
       [:tspan
        {:x 10 :y (- court-y 10)}
        (str "Shots: " shots)]])))


(defn arc
  ([distance]
   (arc {} distance))
  ([attrs distance]
   (let [[hoop-x hoop-y] hoop-coordinates
         x-start (- hoop-x distance)
         [dx dy] [(* 2 distance) 0]
         [rx ry] [distance distance]
         d (string/join " "
                        [\M x-start 0
                         \l 0 hoop-y
                         \a rx ry 0 0 0 dx dy
                         \l 0 (- hoop-y)
                         \Z])]
     [:path (assoc attrs
                   :d d)])))


(defn cos-turns
  [turns]
  (Math/cos (* 2 Math/PI turns)))

(defn sin-turns
  [turns]
  (Math/sin (* 2 Math/PI turns)))


(defn partial-arc
  ([min-angle max-angle distance]
   (partial-arc {} min-angle max-angle distance))
  ([attrs min-angle max-angle distance]
   (let [[hoop-x hoop-y] hoop-coordinates
         [min-x min-y] [(* distance (sin-turns min-angle)) (* distance (cos-turns min-angle))]
         [max-x max-y] [(* distance (sin-turns max-angle)) (* distance (cos-turns max-angle))]
         d (string/join " " [\M hoop-x hoop-y
                             \l min-x min-y
                             \a distance distance 0 0 0 (- max-x min-x) (- max-y min-y)
                             \Z])]
     [:path (assoc attrs
                   :d d)])))


(defn pps->color
  [pps]
  (let [max-pps 3
        grayscale (Math/round (* 255 (/ (- max-pps pps) max-pps)))]
    (str "rgb(" grayscale ", " grayscale ", " grayscale ")")))


(defn shot-chart
  []
  (let [hovered? (reagent/atom false)
        shot-info (reagent/atom {})
        sector-info (reagent/atom {})
        sector-hovered? (reagent/atom false)]
    [court
     {:id "shot-chart"
      :scale 0.8}
     [:g {:stroke-width 0.5 :stroke "black"}
      (let [sector-pps-map (->> (<sub [::subs/pps-by-sector])
                                (map (fn [{:keys [sector pps shots]}]
                                       [sector {:pps pps
                                                :shots shots}]))
                                (into {}))]
        (for [{:keys [max-distance] :as sector} (sort-by :max-distance > (<sub [::subs/sectors]))]
          (let [sector-pps (get sector-pps-map sector)
                pps (get-in sector-pps-map [sector :pps] 0)
                {:keys [shots]
                 :or {shots 0}} sector-pps]
            ^{:key (gensym "key-")}
            [arc {:on-mouse-enter (fn [e]
                                    (reset! sector-info
                                            {:pps pps
                                             :shots shots})
                                    (reset! sector-hovered? true))
                  :on-mouse-leave #(reset! sector-hovered? false)
                  :fill (pps->color pps)} max-distance])))]
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
        action pts])
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
    [selection-list
     :src (at)
     :height     "160px"
     :model          selections
     :choices        teams
     :label-fn       :team/name
     :id-fn :db/id
     :multi-select?  false
     :on-change      (fn [sel]
                       (re-frame/dispatch
                        [::events/set-shot-chart-teams sel]))]))


(defn analysis-games-selector
  []
  (let [games (sort-by :game/datetime (<sub [::subs/games]))
        selections (<sub [::subs/shot-chart-games])]
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
                        [::events/set-shot-chart-games sel]))]))


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


(defn analysis
  []
  [:div.flex.flex-col.items-start
   [:div.flex.gap-2
    [shot-chart]
    [analysis-stats]]
   [analysis-players-selector]
   [analysis-offense-selector]
   [:div.flex
    [analysis-teams-selector]
    [analysis-games-selector]]])


(defn main-panel
  []
  [:div.container.mx-4.my-4.flex.flex-col.gap-4
   {:class "w-11/12"}
   [game-input]
   [game-stats]
   [game-controls]
   [analysis]])
