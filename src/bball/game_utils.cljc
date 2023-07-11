(ns bball.game-utils
  (:require
   [bball.schema :as schema]
   [datascript.core :as datascript]))


(def allowed-keys (->> schema/schema
                       (remove :db/tupleAttrs)
                       (map :db/ident)))


(defn pull-result->tx-map
  [pull-result]
  (cond
    (set? pull-result) (vec pull-result)
    (map? pull-result) (reduce-kv
                        (fn [m k v]
                          (let [ft-results? (= :ft/results k)]
                            (if (and ft-results? (empty? v))
                              m ;; don't include empty :ft/results
                              (assoc m k
                                     (if ft-results?
                                       (->> (concat v (repeat nil))
                                            (take (max 2 (count (filter boolean? v))))
                                            vec) ;; make sure :ft/results is minimum length 2, for tuple compliance
                                       (pull-result->tx-map v) ;; otherwise, recursively apply
                                       )))))
                        {}
                        (select-keys pull-result allowed-keys))
    (vector? pull-result) (vec (map pull-result->tx-map pull-result))
    :else pull-result))


(def pull-pattern
  '[* {:game/home-team [:team/name]
       :game/away-team [:team/name]
       :game/possession [* {:possession/team [:team/name]}]}])


(defn datascript-game->tx-map
  [db g]
  (pull-result->tx-map (datascript/pull db pull-pattern g)))


(comment
  (datascript-game->tx-map
   (datascript/db-with
    (datascript/empty-db (schema/datomic->datascript-schema schema/schema))
    [{:db/id 5000
      :game/possession [{:action/type :action.type/shot
                         :shot/value 3
                         :shot/make? true}]}])
   5000)
  )

