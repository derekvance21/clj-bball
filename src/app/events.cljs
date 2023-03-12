(ns app.events
  (:require
   [re-frame.core :as re-frame]
   [app.db :as db]
   [clojure.edn :as edn]
   [bball.parser :as p]
   [datascript.core :as d]))

(re-frame/reg-event-fx
 ::initialize
 [(re-frame/inject-cofx ::db/datascript-conn)]
 (fn [{:keys [conn]} _]
   (d/transact! conn [{:game/teams [{:team/name "Blaine"}
                                    {:team/name "Zillah"}]}])
   (let [{gid :db/id
          teams :game/teams}
         (d/pull @conn '[:db/id {:game/teams [:db/id :team/name]}]
                 (d/q '[:find ?g .
                        :where
                        [?g :game/teams]]
                      @conn))]
     {:db {:game {:id gid
                  :teams teams}}})))

(defn parse-input
  [input]
  (try (-> input edn/read-string p/parse)
       (catch js/Object e e)))

(defn last-poss
  [db g]
  (let [o (d/q '[:find (max ?o) .
                 :in $ ?g
                 :where
                 [?g :game/possession ?p]
                 [?p :possession/order ?o]]
               db g)
        p (d/q '[:find ?p .
                 :in $ ?g ?o
                 :where
                 [?g :game/possession ?p]
                 [?p :possession/order ?o]]
               db g o)]
    (if p (d/pull db '[*] p) nil)))

(re-frame/reg-event-fx
 ::add-action
 [(re-frame/inject-cofx ::db/datascript-conn)]
 (fn [{:keys [conn db]} _]
   (let [action (edn/read-string (:action-input db))
         team (:team db)
         g (get-in db [:game :id])
         poss (last-poss @conn g)
         cteam (:possession/team poss)
         cop? (not= (:db/id team) (:db/id cteam))
         tx (if cop?
              {:db/id g
               :game/possession [{:possession/order (inc (or (:possession/order poss) -1))
                                  :possession/team {:team/name (:team/name team)}
                                  :possession/action [action]}]}
              {:db/id (:db/id poss)
               :possession/action [action]})]
     (println tx)
     (d/transact! conn [tx])
     {:db (assoc-in db [:game :score]
                    (d/q db/q-score @conn db/rules))})))

(re-frame/reg-event-db
 ::select-team
 (fn [db [_ team]]
   (assoc db :team team)))

(re-frame/reg-event-db
 ::set-action
 (fn [db [_ action-input]]
   (assoc db :action-input action-input)))