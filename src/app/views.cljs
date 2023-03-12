(ns app.views
  (:require
   [re-frame.core :as re-frame]
   [app.subs :as subs]
   [app.events :as events]))

(defn score []
  (let [[[team1 score1] [team2 score2]] @(re-frame/subscribe [::subs/score])]
    [:div.grid.grid-cols-2.self-center.my-4.justify-items-center
     [:h2.text-xl team1] [:h2.text-xl team2]
     [:code.text-3xl.font-bold score1] [:code.text-3xl.font-bold score2]]))

(defn action-input []
  (let [action-input (re-frame/subscribe [::subs/action-input])]
    [:div
     [:textarea.text-sm {:rows 10
                         :cols 24
                         :on-change #(re-frame/dispatch [::events/set-action (-> % .-target .-value)])
                         :value @action-input
                         :spellCheck "false"}]
     [:button {:on-click #(re-frame/dispatch [::events/add-action])}
      "Add"]]))

(defn render-shot
  [action]
  (let [make? (:shot/make? action)
        value (:shot/value action)]
    [:div
     [:p (str value " PT " (if make? "make" "miss"))]]))

(defn render-action
  [action]
  (let [player (:action/player action)
        type (:action/type action)
        team (:team/name action)]
    [:div
     [:p (str team " #" player)]
     (if (= type :action.type/shot)
       [render-shot action]
       [:p type])]))

(defn actions
  []
  (let [actions (re-frame/subscribe [::subs/actions])]
    [:ul
     (for [action actions]
       ^{:key (:db/id action)} [render-action action])]))

(defn team-selector
  []
  (let [[team1 team2] @(re-frame/subscribe [::subs/teams])]
    [:div.flex.flex-col
     [:button
      {:on-click #(re-frame/dispatch [::events/select-team team1])}
      (:team/name team1)]
     [:button
      {:on-click #(re-frame/dispatch [::events/select-team team2])}
      (:team/name team2)]]))

(defn main-panel []
  [:div.container.w-96.mx-4.my-2.flex.flex-col
   [score]
   [team-selector]
   [action-input]
   ])
