(ns app.db)


(def init-db
  {:init {:init/period 1}
   :shot-chart {:show-zones? true
                :show-shots? true
                :zone-by :pps}})


(defn reboundable?
  ([action]
   (let [{:shot/keys [make?]
          :action/keys [type]
          :ft/keys [results]} action
         fts? (not (empty? results))]
     (and (not= type :action.type/technical)
          (if fts?
            (false? (last results))
            (and (= type :action.type/shot) (not make?)))))))

(defn ft?
  [{:action/keys [type]
    :shot/keys [foul?]}]
  (or (= type :action.type/bonus)
      (= type :action.type/technical)
      foul?))

