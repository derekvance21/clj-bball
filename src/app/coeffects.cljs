(ns app.coeffects
  (:require
   [app.datascript :as ds]
   [re-frame.core :as re-frame]))


(re-frame/reg-cofx
 ::local-storage-game
 (fn [cofx _]
   (if-some [game-tx-map (ds/local-storage->game)]
     (assoc cofx :ls-game game-tx-map)
     cofx)))


(re-frame/reg-cofx
 ::ds
 (fn [cofx _]
   (assoc cofx :ds @ds/conn)))


(def inject-ds (re-frame/inject-cofx ::ds))

