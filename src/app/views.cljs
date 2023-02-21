(ns app.views
  (:require
   [re-frame.core :as re-frame]
   [app.subs :as subs]
   [app.events :as events]
   [bball.parser :as p]))

(defn text-input []
  (let [name (re-frame/subscribe [::subs/name])]
    [:div
     [:input {:type "text"
              :value @name
              :on-change #(re-frame/dispatch [::events/set-name (-> % .-target .-value)])}]]))

(defn main-panel []
  (let [name (re-frame/subscribe [::subs/name])]
    [:div.container.mx-4.my-2
     [:h1.text-2xl
      "Hello from " [:span.font-bold @name]]
     [text-input]]))
