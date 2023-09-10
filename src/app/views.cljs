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
    [:div.container.m-2.flex.flex-col.gap-2
     {:class "w-11/12"}
     [:div.flex.gap-2
      [button {:class "px-2 py-1"
               :selected? game?
               :on-click #(re-frame/dispatch [::events/set-active-panel :game])}
       "Game"]
      [button {:class "px-2 py-1"
               :selected? analysis?
               :on-click #(re-frame/dispatch [::events/set-active-panel :analysis])}
       "Analysis"]]
     (cond
       game? [game]
       analysis? [analysis]
       :else nil)]))
