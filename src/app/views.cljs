(ns app.views
  (:require
   [re-frame.core :as re-frame]
   [app.subs :as subs]
   [app.events :as events]))

(defn text-input []
  (let [name (re-frame/subscribe [::subs/name])]
    [:div
     [:input {:type "text"
              :value @name
              :on-change #(re-frame/dispatch [::events/set-name (-> % .-target .-value)])}]]))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn main-panel []
  (let [name (re-frame/subscribe [::subs/name])]
    [:div
     [:h1
      "Hello frog " @name]
     [text-input]]))
