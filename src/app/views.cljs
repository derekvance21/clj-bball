(ns app.views
  (:require [app.events :as events]
            [app.subs :as subs]
            [re-frame.core :as re-frame]))


;; TODO - use this more
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


(defn stats []
  (let [[team1 team2] (<sub [::subs/teams])
        t1 (:db/id team1)
        t2 (:db/id team2)
        team1-possession? (<sub [::subs/team-has-possession? t1])
        team2-possession? (<sub [::subs/team-has-possession? t2])
        period (<sub [::subs/period])]
    [:div
     [:div.flex.justify-between
      [:h2.text-xl.border-y-4
       {:class [(if team1-possession? "border-solid" "border-transparent")]}
       (:team/name team1)]
      [:button.self-center {:type "button"
                            :on-click #(re-frame/dispatch [::events/next-period])}
       (period->string period 4 "Q")]
      [:h2.text-xl.border-y-4
       {:class [(if team2-possession? "border-solid" "border-transparent")]}
       (:team/name team2)]]
     [:div.flex.justify-between
      [:code.text-3xl.font-bold (<sub [::subs/team-score t1])]
      [:code.text-3xl.font-bold (<sub [::subs/team-score t2])]]
     [stat "PPP" #(.toFixed % 2) [::subs/team-ppp t1] [::subs/team-ppp t2]]
     [stat "PPS" #(.toFixed % 2) [::subs/team-pps t1] [::subs/team-pps t2]]
     [stat "eFG%" #(-> % (* 100) .toFixed) [::subs/team-efg t1] [::subs/team-efg t2]]
     [stat "OffReb%" #(-> % (* 100) .toFixed) [::subs/team-off-reb-rate t1] [::subs/team-off-reb-rate t2]]
     [stat "TOV%" #(-> % (* 100) .toFixed) [::subs/team-turnover-rate t1] [::subs/team-turnover-rate t2]]
     [stat "FT/FGA" #(.toFixed % 2) [::subs/team-ft-rate t1] [::subs/team-ft-rate t2]]]))


(defn player-input
  [offense? player-sub player-event label]
  (let [player (re-frame/subscribe player-sub)
        players (<sub [(if offense? ::subs/offense-players ::subs/defense-players)])
        on-player-change (fn [e]
                           (let [p (-> e .-target .-value parse-long)]
                             (when p (re-frame/dispatch [player-event p]))))]
    [:label
     [:select.w-12 {:on-change on-player-change
                    :value (or @player "")
                    :required true}
      (when-not @player [:option {:value ""} ""])
      (for [[i p] (zipmap (range) players)]
        [:option {:key i :value p} (str p)])]
     label]))


(defn rebound-input []
  (let [off-reb? (re-frame/subscribe [::subs/off-reb?])]
    [:div.flex.flex-col
     [:label
      [:input {:type "checkbox"
               :on-change #(re-frame/dispatch [::events/set-off-reb? (-> % .-target .-checked)])
               :checked (boolean @off-reb?)}]
      "Off-Reb?"]
     [player-input @off-reb? [::subs/rebounder] ::events/set-rebounder "Rebounder"]]))


(defn foul-input []
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


(defn shot-input []
  (let [value (re-frame/subscribe [::subs/shot-value])
        make? (re-frame/subscribe [::subs/shot-make?])
        foul? (re-frame/subscribe [::subs/foul?])]
    [:div.flex.flex-col.ml-4
     [:label
      [:input.w-12 {:type "number"
                    :on-change #(re-frame/dispatch [::events/set-shot-value (-> % .-target .-value parse-long)])
                    :value @value
                    :min 2
                    :max 3
                    :required true}]
      "Value"]
     [:label
      [:input.w-12 {:type "number"
                    :on-change #(re-frame/dispatch [::events/set-shot-distance (-> % .-target .-value parse-long)])
                    :value (<sub [::subs/shot-distance])
                    :min (if (= @value 3) 19 0)
                    :max (if (= @value 2) 19 90)
                    :required true}]
      "Distance"]
     [:label
      [:input {:type "checkbox"
               :on-change #(re-frame/dispatch [::events/set-shot-make? (-> % .-target .-checked)])
               :checked (boolean @make?)}]
      "Make?"]
     [:label
      [:input {:type "checkbox"
               :on-change #(re-frame/dispatch [::events/set-shot-foul? (-> % .-target .-checked)])
               :disabled (nil? @value)
               :checked (boolean @foul?)}]
      "Foul?"]
     (when @foul?
       [foul-input])]))


(defn turnover-input []
  [player-input false [::subs/stealer] ::events/set-stealer "Stealer"])


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


(defn players-input
  []
  [:div
   (doall
    (for [team (<sub [::subs/teams])]
      [:div {:key (:db/id team)}
       [:h2 (:team/name team)]
       [:div.flex
        (for [[i player] (zipmap (range) (<sub [::subs/team-players (:db/id team)]))]
          [:input.w-12
           {:key i
            :type "number"
            :value player
            :min 0
            :max 99
            :required true
            :on-change #(re-frame/dispatch [::events/set-on-court-player
                                            (:db/id team) i
                                            (-> % .-target .-value parse-long)])}])]]))])


(defn submit-action-input
  [e]
  (.preventDefault e)
  (re-frame/dispatch [::events/add-action]))


(defn action-input []
  (let [type (re-frame/subscribe [::subs/action-type])]
    [:form {:on-submit submit-action-input}
     [:div.flex.flex-col
      [player-input true [::subs/action-player] ::events/set-player "Player"]
      [:label
       [:input {:type "radio"
                :value :action.type/shot
                :name :action/type
                :on-change #(when (-> % .-target .-checked) (re-frame/dispatch [::events/set-action-shot]))
                :checked (= @type :action.type/shot)}]
       "Shot"]
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
        [shot-input])
      (when (= @type :action.type/turnover)
        [turnover-input])
      (when (= @type :action.type/bonus)
        [bonus-input])
      (when (= @type :action.type/technical)
        [foul-input])
      (when (<sub [::subs/reboundable?])
        [rebound-input])
      [players-input]
      [:button.self-start {:type "submit"}
       "Add"]]]))


(defn render-fts
  [ftm fta]
  [:span (str ftm "/" fta " FTs")])


(defn render-rebound
  [rebounder off-reb?]
  [:span (if rebounder (str " #" rebounder) " Team") " " (if off-reb? "OffReb" "DefReb")])


(defn render-shot
  [action]
  (let [{make? :shot/make? value :shot/value distance :shot/distance off-reb? :shot/off-reb? rebounder :shot/rebounder fta :ft/attempted ftm :ft/made} action]
    [:span
     (str value " PT (" distance "') " (if make? "make " "miss "))
     (when (and fta (> fta 0))
       [render-fts ftm fta])
     (when rebounder
       [render-rebound rebounder off-reb?])]))


(defn render-turnover
  [action]
  (let [{stealer :turnover/stealer} action]
    [:span (str "turnover" (when stealer (str " #" stealer " Steal")))]))


(defn render-bonus
  [action]
  (let [{ftm :ft/made fta :ft/attempted rebounder :shot/rebounder off-reb? :shot/off-reb?} action]
    [:span "bonus "
     [render-fts ftm fta]
     (when rebounder
       [render-rebound rebounder off-reb?])]))


(defn render-technical
  [action]
  (let [{ftm :ft/made fta :ft/attempted} action]
    [:span "technical "
     [render-fts ftm fta]]))


(defn render-action
  [action]
  (let [{player :action/player type :action/type} action]
    [:div
     [:span (str "#" player " ")]
     (case type
       :action.type/shot [render-shot action]
       :action.type/turnover [render-turnover action]
       :action.type/bonus [render-bonus action]
       :action.type/technical [render-technical action]
       [:span type])]))


(defn render-possession
  [possession team-map]
  (let [{actions :possession/action {t :db/id} :possession/team order :possession/order} possession
        team (get-in team-map [t :team/name])]
    [:div
     [:p.font-bold (str order ". " team)]
     [:ul.ml-8
      (for [action actions]
        [:li {:key (:db/id action)} [render-action action]])]]))


(defn possessions
  []
  (let [possessions (<sub [::subs/sorted-possessions])
        [team1 team2] (<sub [::subs/teams])
        team-map {(:db/id team1) team1 (:db/id team2) team2}]
    [:div.my-4
     [:h2.text-xl "Possessions"]
     [:ol.max-h-64.overflow-auto
      (for [possession possessions]
        [:li {:key (:db/id possession)} [render-possession possession team-map]])]]))


(defn team-selector
  [disabled?]
  (let [teams (re-frame/subscribe [::subs/teams])
        [team1 team2] @teams
        team (re-frame/subscribe [::subs/team])]
    [:div.flex
     [:label [:input {:type "radio"
                      :value team1
                      :name :team
                      :checked (= @team team1)
                      :disabled disabled?
                      :on-change #(when (-> % .-target .-checked) (re-frame/dispatch [::events/set-team team1]))}]
      (:team/name team1)]
     [:label.ml-2
      [:input {:type "radio"
               :value team2
               :name :team
               :checked (= @team team2)
               :disabled disabled?
               :on-change #(when (-> % .-target .-checked) (re-frame/dispatch [::events/set-team team2]))}]
      (:team/name team2)]]))


(defn main-panel []
  [:div.container.mx-4.my-4.flex.justify-between
   {:class "w-1/2"}
   [:div.flex.flex-col.flex-1
    [team-selector (not (<sub [::subs/possessions?]))]
    [action-input]]
   [:div.flex.flex-col.flex-1
    [stats]
    [possessions]]])

