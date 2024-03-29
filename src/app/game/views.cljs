(ns app.game.views
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [app.events :as events]
   [app.subs :as subs]
   [cljs.math :as math]
   [app.utils.views :refer [button <sub court]]
   [app.utils :refer [court-dimensions hoop-coordinates client->polar-hoop polar-hoop->eucl-court]]))


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
     [:ul.flex.flex-col.gap-1
      {:class (when-not
               (if sub? ft? (or ft? reboundable?))
                "invisible")}
      (for [player (if ft? ft-players players)]
        [:li {:key (str team-id "#" player "secondary")}

         [button
          {:selected? (cond reboundable? (rebounder? player)
                            :else false)
           :disabled? (if sub? (= player action-player) (not reboundable?))
           :on-click (cond (and sub? ft?) #(re-frame/dispatch [::events/put-player-to-ft-bench team-id player])
                           reboundable? #(re-frame/dispatch [::events/set-rebounder true player]))
           :class "w-8 h-8"}
          (str player)]])]]))


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
    [:div.border.border-2.rounded-md.px-2.py-1.flex.flex-col.gap-1
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
    [:div.flex.gap-1.md:flex-col.items-baseline.md:items-stretch
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
     [:ol.max-h-52.overflow-auto.flex.flex-col
      (for [possession possessions]
        [:li
         {:key (:db/id possession)}
         [render-possession possession {:preview-entities preview-entities}]])]]))


(defn svg-click-handler
  [court-id make?]
  (fn [e]
    (let [[turns radius] (client->polar-hoop
                          court-id court-dimensions hoop-coordinates
                          [(.-clientX e) (.-clientY e)])]
      (re-frame/dispatch [::events/set-action-shot-with-info turns (math/round radius) make?]))))


(defn svg-right-click-handler
  [court-id]
  (fn [e]
    (.preventDefault e)
    ((svg-click-handler court-id true) e)))


(defn action-input []
  (let [shot-location (<sub [::subs/shot-location])
        id "game-court"
        type (re-frame/subscribe [::subs/action-type])]
    [:form.flex.flex-col.gap-1
     {:on-submit (fn submit-action-input
                   [e]
                   (.preventDefault e)
                   (re-frame/dispatch [::events/add-action]))}
     [:div.flex.flex-col.gap-1
      [:div
       {:style {:width "420px"}}
       [court
        {:id id
         :on-context-menu (svg-right-click-handler id)
         :on-click (svg-click-handler id false)}
        (when (some? shot-location)
          (let [[x y] (polar-hoop->eucl-court hoop-coordinates shot-location)
                icon-size 5]
            [:circle
             {:r icon-size :cx x :cy y
              :fill "none"
              :stroke (if (<sub [::subs/shot-make?]) "green" "red")
              :stroke-width 2}]))]]
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


(defn game-input
  []
  [:div.flex.justify-between.gap-2.flex-col.md:flex-row
   [:div.order-last.md:order-none
    [players-input]]
   [action-input]])


(defn new-game
  []
  [:button.bg-red-500.hover:bg-red-700.text-white.font-bold.py-1.px-2.rounded-full
   {:type "button"
    :on-click #(re-frame/dispatch [::events/start-new-game])}
   "New Game"])


;; TODO - this will eventually post to the server
(defn submit-game
  []
  [:button.self-start.bg-indigo-500.hover:bg-indigo-600.text-white.font-bold.py-1.px-2.rounded-full
   {:type "button"
    :on-click #(re-frame/dispatch [::events/download-game])}
   "Download"])


(defn game-controls
  []
  [:div.flex.gap-2
   [new-game]
   [submit-game]])


(defn game
  []
  [:div.flex.flex-col.gap-2
   [:div.flex.flex-wrap.md:flex-nowrap.gap-4
    [game-input]
    [:div.flex-1
     [stats]
     [possessions]]]
   [game-controls]])
