(ns app.db)


(def init-db
  {:init {:init/period 1}})


(defn reboundable?
  ([action]
   (let [{:shot/keys [make? foul?]
          :action/keys [type]
          :ft/keys [made attempted]} action]
     (reboundable? type make? foul? made attempted)))
  ([type make? foul? ftm fta]
   (let [fts? (or foul? (= :action.type/bonus type)) ;; no action.type/technical, because they're not reboundable
         shot? (= type :action.type/shot)]
     (or (and fts? (< ftm fta))
         (and shot? (not fts?) (not make?))))))

