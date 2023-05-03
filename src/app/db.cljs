(ns app.db)


(def init-db
  {:init {:init/period 1}})


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

