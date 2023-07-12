(ns bball.scratch
  (:require
   [datascript.core :as d]
   [bball.schema :as schema]
   [bball.query :as query]
   [clojure.edn :as edn]))


(def blaine-games
  ["resources/games/2023-01-05-Sedro-Woolley-Blaine.edn"
   "resources/games/2023-01-10-Blaine-Meridian.edn"
   "resources/games/2023-01-12-Blaine-Lynden.edn"
   "resources/games/2023-01-16-Mount-Baker-Blaine.edn"
   "resources/games/2023-01-20-Blaine-Burlington-Edison.edn"
   "resources/games/2023-01-23-Mount-Vernon-Blaine.edn"
   "resources/games/2023-01-26-Blaine-Lynden-Christian.edn"
   "resources/games/2023-01-28-Blaine-Anacortes.edn"
   "resources/games/2023-02-01-Sehome-Blaine.edn"
   "resources/games/2023-02-02-Blaine-Ferndale.edn"
   "resources/games/2023-02-08-Blaine-Meridian.edn"
   "resources/games/2023-02-11-Lynden-Christian-Blaine.edn"
   "resources/games/2023-02-14-Blaine-Nooksack-Valley.edn"
   "resources/games/2023-02-18-Northwest-Blaine.edn"
   "resources/games/2023-02-25-Zillah-Blaine.edn"])


(def d-schema (schema/datomic->datascript-schema schema/schema))


(def conn (d/create-conn d-schema))


(def tx-data
  (map (comp edn/read-string slurp) blaine-games))


(comment
  
  (d/transact!
   conn
   tx-data)
  )


(def score-query
  '[:find ?g (pull ?t [*]) (sum ?pts)
    :keys game-id team points
    :in $ %
    :with ?a
    :where
    (actions ?g ?t ?p ?a)
    (pts ?a ?pts)])


(->> (d/q score-query @conn query/rules)
     (sort-by :game-id))


(def shots-query
  '[:find (pull ?a [:shot/make? :shot/value :shot/distance :shot/angle]) ?pts
    :in $ %
    :where
    [?a :action/type :action.type/shot]
    (pts ?a ?pts)])


(def pps-query
  '[:find (avg ?pts) .
    :in $ %
    :with ?a
    :where
    [?a :action/type :action.type/shot]
    (pts ?a ?pts)])

(float (d/q pps-query @conn query/rules))

(def ppp-query
  '[:find (pull ?t [:team/name]) (sum ?pts) (count-distinct ?p)
    :keys team total-pts num-possessions
    :in $ %
    :with ?a
    :where
    (actions ?g ?t ?p ?a)
    (pts ?a ?pts)])

(->> (d/q ppp-query @conn query/rules)
     (map (fn [{:keys [team total-pts num-possessions]}]
            {:team team :ppp (float (/ total-pts num-possessions)) :num-possessions num-possessions}))
     (sort-by :ppp))


(->> (d/q '[:find ?team ?numbers ?sector (avg ?pts) (count ?a)
            :in $ % [[?team ?numbers] ...]
            :where
            (actions ?g ?t ?p ?a)
            [?a :action/player ?player]
            [(contains? ?numbers ?player)]
            [?a :shot/distance ?inches]
            [?a :shot/value ?value]
            (sector ?value ?inches ?sector)
            (pts ?a ?pts)
            [?t :team/name ?team]]
          @conn
          query/rules
          (map vector (repeat "Blaine") [#{1} #{2 5} #{3} #{4} #{35} #{42} #{20}]))
     (sort-by (fn [[_ _ sector _]] (->> ["0-3" "3-10" "10-3P" "3P-24" "24+"]
                                        (map-indexed vector)
                                        (filter #(= sector (second %)))
                                        ffirst)))
     (sort-by (comp first second))
     (map #(update % 3 float)))

(def shots-query-by-player
  '[:find ?t ?numbers (pull ?a [:shot/distance :shot/angle :shot/make?]) ?pts
    :in $ % ?t ?numbers
    :where 
    [?p :possession/team ?t]
    [?p :possession/action ?a]
    [?a :action/type :action.type/shot]
    [?a :action/player ?player]
    [(contains? ?numbers ?player)]
    (pts ?a ?pts)])


(count (d/q shots-query-by-player @conn query/rules [:team/name "Blaine"] #{4}))

(def player-shots-query
  '[:find (pull ?t [:team/name]) ?player (pull ?a [:shot/distance :shot/angle :shot/make?]) ?pts
    :keys team player shot pts
    :in $ %
    :where
    [?p :possession/team ?t]
    [?p :possession/action ?a]
    [?a :action/type :action.type/shot]
    [?a :action/player ?player]
    (pts ?a ?pts)])


(->> (update-vals
      (->> (d/q player-shots-query @conn query/rules)
           (group-by (juxt :team :player)))
      count)
     (sort-by val >)
     (take 20))
;; => ([[#:team{:name "Blaine"} 1] 189]
;;     [[#:team{:name "Blaine"} 3] 186]
;;     [[#:team{:name "Blaine"} 35] 154]
;;     [[#:team{:name "Blaine"} 42] 138]
;;     [[#:team{:name "Blaine"} 4] 73]
;;     [[#:team{:name "Blaine"} 2] 43]
;;     [[#:team{:name "Blaine"} 5] 35]
;;     [[#:team{:name "Blaine"} 20] 33]
;;     [[#:team{:name "Mount Vernon"} 10] 29]
;;     [[#:team{:name "Lynden Christian"} 35] 28]
;;     [[#:team{:name "Meridian"} 5] 27]
;;     [[#:team{:name "Meridian"} 24] 25]
;;     [[#:team{:name "Zillah"} 34] 25]
;;     [[#:team{:name "Lynden Christian"} 0] 23]
;;     [[#:team{:name "Sedro-Woolley"} 5] 23]
;;     [[#:team{:name "Nooksack Valley"} 12] 21]
;;     [[#:team{:name "Sehome"} 4] 21]
;;     [[#:team{:name "Anacortes"} 4] 21]
;;     [[#:team{:name "Lynden Christian"} 13] 20]
;;     [[#:team{:name "Meridian"} 1] 20])

(def player-pts-game-query
  '[:find (pull ?g [{:game/home-team [:team/name] :game/away-team [:team/name]}]) (pull ?t [:team/name]) ?player (sum ?pts)
    :keys game team player pts
    :in $ %
    :with ?a
    :where
    (actions ?g ?t ?p ?a)
    [?a :action/player ?player]
    (pts ?a ?pts)])

(->> (d/q player-pts-game-query @conn query/rules)
     (map #(update % :game (juxt (comp :team/name :game/home-team) (comp :team/name :game/away-team))))
     (map #(update % :team :team/name))
     (remove #(= "Blaine" (:team %)))
     (sort-by :pts >))


;; 33000 datoms
;; 1700 shots