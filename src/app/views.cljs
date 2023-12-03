(ns app.views
  (:require
   [app.events :as events]
   [app.subs :as subs]
   [app.utils.views :refer [button <sub]]
   [app.analysis.views :refer [analysis]]
   [app.game.views :refer [game]]
   [re-frame.core :as re-frame]))


(defn main-panel
  []
  (let [active-panel (<sub [::subs/active-panel])
        game? (= active-panel :game)
        analysis? (= active-panel :analysis)]
    [:div.container.mx-auto
     {:class "lg:w-3/4"}
     ;; header
     [:div.flex.gap-2.px-2.py-1
      [button {:class "px-2 py-1"
               :selected? analysis?
               :on-click #(re-frame/dispatch [::events/set-active-panel :analysis])}
       "Analysis"]
      [button {:class "px-2 py-1"
               :selected? game?
               :on-click #(re-frame/dispatch [::events/set-active-panel :game])}
       "Game"]]
     ;; main
     [:div.m-2
      (cond
        game? [game]
        analysis? [analysis])]]))
