(ns app.views
  (:require
   [re-frame.core :as re-frame]
   [app.subs :as subs]
   [app.events :as events]))

(defn game-input []
  (let [game-input (re-frame/subscribe [::subs/game-input])]
    [:textarea.text-sm {:rows 10
                        :cols 48
                        :on-change #(re-frame/dispatch [::events/set-game-input (-> % .-target .-value)])
                        :value @game-input
                        :spellCheck "false"}]))

(defn game-object []
  (let [game (re-frame/subscribe [::subs/game-object-string])]
    [:div.max-h-48.overflow-auto.my-4
     [:pre [:code.text-xs @game]]]))

(defn game-score []
  (let [[[team1 score1] [team2 score2]] @(re-frame/subscribe [::subs/game-score])]
    [:div.grid.grid-cols-2.self-center.my-4.justify-items-center
     [:h2.text-xl team1] [:h2.text-xl team2]
     [:code.text-3xl.font-bold score1] [:code.text-3xl.font-bold score2]]))

(defn update-game []
  [:div.my-4.self-center
   [:button.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-2.px-4.rounded-full
    {:on-click #(re-frame/dispatch [::events/update-game])}
    "Update"]])

(defn main-panel []
  [:div.container.w-96.mx-4.my-2.flex.flex-col
   [game-score]
   [game-input]
   [update-game]
   [game-object]])
