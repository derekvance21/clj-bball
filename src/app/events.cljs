(ns app.events
  (:require
   [re-frame.core :as re-frame]
   [app.db :as db]
   [clojure.edn :as edn]
   [bball.parser :as p]
   [bball.query :as q]
   [datascript.core :as d]))

(re-frame/reg-event-fx
 ::initialize
 (fn [_ _]
   {:db db/default-db
    :dispatch [::update-game]}))

(re-frame/reg-event-db
 ::set-game-input
 (fn [db [_ game-input]]
   (assoc db :game-input game-input)))

(re-frame/reg-event-fx
 ::update-game
 [(re-frame/inject-cofx ::db/datascript-conn)]
 (fn [{:keys [conn db]} _]
   (let [game-object
         (try (-> (:game-input db) edn/read-string p/parse)
              (catch js/Object e e))]
     (d/reset-conn! conn db/empty-db)
     (when (coll? game-object)
       (d/transact! conn [game-object]))
     {:db (assoc db
                 :game-object game-object
                 :game-score (d/q db/q-score @conn q/rules))})))
