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


(def ft-players
  (re-frame/enrich
   (fn [db event]
     ;; TODO - I think this if statement isn't needed. ft-players is set for ft events, so it'll be a ft?
     (if (db/ft? (:action db))
       (when-not (every? (fn [[team-id players]]
                           (and (contains? players :on-court-ft)
                                (contains? players :on-bench-ft)))
                         (:players db))
         (update db :players (fn [players-map]
                               (->> players-map
                                    (map (fn [[team-id team-players]]
                                           [team-id (assoc team-players
                                                           :on-court-ft (:on-court team-players)
                                                           :on-bench-ft (:on-bench team-players))]))
                                    (into {})))))
       (update db :players (fn [players-map]
                             (->> players-map
                                  (map (fn [[team-id team-players]]
                                         [team-id (dissoc team-players :on-court-ft :on-bench-ft)]))
                                  (into {}))))))))


(def ft
  [ft-players
   ftm
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
            (let [ds (re-frame/get-effect context ::fx/ds)
                  db (re-frame/get-effect context :db)
                  game-id (:game-id db)]
              (if (and (some? ds) (some? db))
                (ds/game->local-storage ds game-id)
                context)))))
