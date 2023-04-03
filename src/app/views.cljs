(ns app.views
  (:require [app.events :as events]
            [app.subs :as subs]
            [re-frame.core :as re-frame]))

;; TODO - use this more
(def <sub  (comp deref re-frame/subscribe))

(defn team-score
  [name score ppp possession?]
  [:div.flex.flex-col.items-center.border-y-4 {:class [(if possession? "border-solid" "border-transparent")]}
   [:h2.text-xl name]
   (when score [:code.text-3xl.font-bold score])
   (when-not (NaN? ppp) [:span (.toFixed ppp 2)])])

(defn score []
  (let [[team1 team2] @(re-frame/subscribe [::subs/teams])
        score-map (when-let [[[t1 score1]
                              [t2 score2]]
                             @(re-frame/subscribe [::subs/score])]
                    {t1 score1 t2 score2})
        score1 (get score-map (:db/id team1) 0)
        score2 (get score-map (:db/id team2) 0)
        team1-possession? (re-frame/subscribe [::subs/team-has-possession? (:db/id team1)])
        team2-possession? (re-frame/subscribe [::subs/team-has-possession? (:db/id team2)])

        ppp (<sub [::subs/ppp])
        ppp1 (get ppp (:db/id team1))
        ppp2 (get ppp (:db/id team2))]
    [:div.flex.gap-x-2
     [team-score (:team/name team1) score1 ppp1 @team1-possession?]
     [team-score (:team/name team2) score2 ppp2 @team2-possession?]]))

(defn stat
  [label display-fn query1 query2]
  [:div.flex
   [:code.flex-1 (some-> (<sub query1)
                         display-fn)]
   [:p.flex-1.text-center label]
   [:code.flex-1.text-right (some-> (<sub query2)
                                    display-fn)]])

(defn stats []
  (let [[team1 team2] (<sub [::subs/teams])
        t1 (:db/id team1)
        t2 (:db/id team2)
        team1-possession? (<sub [::subs/team-has-possession? t1])
        team2-possession? (<sub [::subs/team-has-possession? t2])]
    [:div
     [:div.flex.justify-between
      [:h2.text-xl.border-y-4
       {:class [(if team1-possession? "border-solid" "border-transparent")]}
       (:team/name team1)]
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
     [stat "TO%" #(-> % (* 100) .toFixed) [::subs/team-turnover-rate t1] [::subs/team-turnover-rate t2]]]))

(defn shot-input []
  (let [value (re-frame/subscribe [::subs/shot-value])
        make? (re-frame/subscribe [::subs/shot-make?])
        ftm (re-frame/subscribe [::subs/ft-made])
        fta (re-frame/subscribe [::subs/ft-attempted])
        foul? (re-frame/subscribe [::subs/foul?])
        rebounder (re-frame/subscribe [::subs/rebounder])
        off-reb? (re-frame/subscribe [::subs/off-reb?])
        reboundable? (re-frame/subscribe [::subs/reboundable?])]
    [:div.flex.flex-col.ml-4
     [:label [:input.w-12 {:type "number"
                           :on-change #(re-frame/dispatch [::events/set-shot-value (-> % .-target .-value parse-long)])
                           :value @value
                           :min 2
                           :max 3
                           :required true}]
      "Value"]
     [:label [:input {:type "checkbox"
                      :on-change #(re-frame/dispatch [::events/set-shot-make? (-> % .-target .-checked)])
                      :checked (boolean @make?)}]
      "Make?"]
     [:label [:input {:type "checkbox"
                      :on-change #(re-frame/dispatch [::events/set-shot-foul? (-> % .-target .-checked)])
                      :disabled (nil? @value)
                      :checked (boolean @foul?)}]
      "Foul?"]
     (when @foul?
       [:label
        [:input.w-12 {:type "number"
                      :on-change #(re-frame/dispatch [::events/set-ft-made (-> % .-target .-value parse-long)])
                      :value @ftm
                      :min 0
                      :max @fta
                      :required true}]
        (str "/ " @fta " FTs")])
     (when @reboundable?
       [:div.flex.flex-col
        [:label [:input {:type "checkbox"
                         :on-change #(re-frame/dispatch [::events/set-off-reb? (-> % .-target .-checked)])
                         :checked (boolean @off-reb?)}]
         "Off-Reb?"]
        [:label
         [:input.w-12 {:type "number"
                       :on-change #(re-frame/dispatch [::events/set-rebounder (-> % .-target .-value parse-long)])
                       :value @rebounder
                       :min 0
                       :max 99
                       ;; :required true ; shouldn't be required, b/c could be a miss then make free throw trip
                       }]
         "Rebounder"]])]))

(defn turnover-input []
  (let [stealer (re-frame/subscribe [::subs/stealer])]
    [:label [:input.w-12 {:type "number"
                          :on-change #(re-frame/dispatch [::events/set-stealer (-> % .-target .-value parse-long)])
                          :value @stealer
                          :min 0
                          :max 99}]
     "Stealer"]))

(defn action-input []
  (let [player (re-frame/subscribe [::subs/action-player])
        type (re-frame/subscribe [::subs/action-type])]
    [:form {:on-submit (fn [e]
                         (.preventDefault e)
                         (re-frame/dispatch [::events/add-action]))}
     [:div.flex.flex-col
      [:label [:input.w-12 {:type "number"
                            :on-change #(re-frame/dispatch [::events/set-action-player (-> % .-target .-value parse-long)])
                            :value @player
                            :min 0
                            :max 99
                            :required true}]
       "Player"]
      [:label [:input {:type "radio"
                       :value :action.type/shot
                       :name :action/type
                       :on-change #(when (-> % .-target .-checked) (re-frame/dispatch [::events/set-action-shot]))
                       :checked (= @type :action.type/shot)}]
       "Shot"]
      (when (= @type :action.type/shot)
        [shot-input])

      [:label [:input {:type "radio"
                       :value :action.type/turnover
                       :name :action/type
                       :on-change #(when (-> % .-target .-checked) (re-frame/dispatch [::events/set-action-turnover]))
                       :checked (= @type :action.type/turnover)}]
       "Turnover"]
      (when (= @type :action.type/turnover)
        [turnover-input])
      [:button.self-start {:type "submit"}
       "Add"]]]))

(defn render-shot
  [action]
  (let [{make? :shot/make? value :shot/value off-reb? :shot/off-reb? rebounder :shot/rebounder fta :ft/attempted ftm :ft/made} action]
    [:span
     (str value " PT " (if make? "make" "miss")
          (when (and fta (> fta 0))
            (str " " ftm "/" fta " FTs"))
          (when rebounder
            (str " " (if rebounder (str "#" rebounder) "Team") " " (if off-reb? "OffReb" "DefReb"))))]))

(defn render-turnover
  [action]
  (let [{stealer :turnover/stealer} action]
    [:span (str "turnover" (when stealer (str " #" stealer " Steal")))]))

(defn render-action
  [action]
  (let [{player :action/player type :action/type id :db/id} action]
    [:div
     (comment {:on-click #(re-frame/dispatch [::events/set-action id])}) ;; TODO - action edit and update
     [:span (str "#" player " ")]
     (case type
       :action.type/shot [render-shot action]
       :action.type/turnover [render-turnover action]
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
  []
  (let [teams (re-frame/subscribe [::subs/teams])
        [team1 team2] @teams
        team (re-frame/subscribe [::subs/team])]
    [:div.flex
     [:label [:input {:type "radio"
                      :value team1
                      :name :team
                      :checked (= @team team1)
                      :on-change #(when (-> % .-target .-checked) (re-frame/dispatch [::events/set-team team1]))}]
      (:team/name team1)]
     [:label.ml-2
      [:input {:type "radio"
               :value team2
               :name :team
               :checked (= @team team2)
               :on-change #(when (-> % .-target .-checked) (re-frame/dispatch [::events/set-team team2]))}]
      (:team/name team2)]]))

(defn box-score []
  (let [box-score (<sub [::subs/box-score])
        teams (<sub [::subs/teams])]
    [:div
     (for [team teams]
       [:div {:key (:db/id team)}
        [:p (:team/name team)]
        (for [[player pts] (get box-score (:db/id team))]
          [:p {:key (str (:db/id team) "#" player)} (str "#" player ": " pts " pts")])])]))

(defn main-panel []
  [:div.container.mx-4.my-4.flex.justify-between
   {:class "w-1/2"}
   [:div.flex.flex-col.flex-1
    (when (<sub [::subs/possessions?])
      [team-selector])
    [action-input]]
   [:div.flex.flex-col.flex-1
    [stats]
    [possessions]]])
