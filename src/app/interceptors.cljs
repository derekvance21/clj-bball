(ns app.interceptors
  (:require
   [cljs.math :as math]
   [re-frame.core :as re-frame]
   [app.db :as db]
   [app.effects :as fx]
   [app.datascript :as ds]))


(def fta
  (re-frame/on-changes
   (fn [value make? foul?]
     (if foul?
       (if make? 1 value)
       0))
   [:action :ft/attempted]
   [:action :shot/value] [:action :shot/make?] [:action :shot/foul?]))


(def ftm
  (re-frame/on-changes
   (fn [results] (->> results (filter true?) count))
   [:action :ft/made]
   [:action :ft/results]))


(def ft-results
  (re-frame/on-changes
   (fn [attempts]
     (vec (repeat attempts false)))
   [:action :ft/results]
   [:action :ft/attempted]))


(def ft
  [ftm
   ft-results
   fta])


(def shot-value
  (re-frame/on-changes
   (fn [distance angle]
     (when (and (some? distance) (some? angle))
       (let [three-point-distance (+ (* 12 19) 10)
             three? (if (> (abs angle) 0.25) ;; if below 3 point arc break
                      (>= (abs (* (math/sin (* angle 2 math/PI)) distance)) three-point-distance) ;; 
                      (>= distance three-point-distance))]
         (if three? 3 2))))
   [:action :shot/value]
   [:action :shot/distance] [:action :shot/angle]))


(def rebound
  (re-frame/enrich
   (fn [db event]
     (when-not (db/reboundable? (:action db))
       (update db :action dissoc :rebound/off? :rebound/player :rebound/team?)))))


(def ds->local-storage
  (re-frame/->interceptor
   :id ::ds->local-storage
   :after (fn [context]
            (when-some [new-ds (re-frame/get-effect context ::fx/ds)]
              (ds/db->local-storage new-ds))
            context)))

