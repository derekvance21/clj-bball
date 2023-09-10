(ns app.utils
  (:require
   [cljs.math :as math]))


(defn cos-turns
  [turns]
  (Math/cos (* 2 Math/PI turns)))


(defn sin-turns
  [turns]
  (Math/sin (* 2 Math/PI turns)))


(defn bounding-rect-xy
  [id]
  (let [bounding-client-rect (.getBoundingClientRect (.getElementById js/document id))]
    [(.-left bounding-client-rect)
     (.-top bounding-client-rect)]))


(defn polar-hoop->eucl-court
  [[hoop-x hoop-y] [turns radius]]
  (let [radians (* turns 2 math/PI)]
    [(-> radians math/sin (* radius) (+ hoop-x))
     (-> radians math/cos (* radius) (+ hoop-y))]))


(defn eucl-hoop->polar-hoop
  [[x y]]
  (let [turns (/ (math/atan2 x y) 2 math/PI)
        radius (math/sqrt (+ (* x x) (* y y)))]
    [turns radius]))


(defn eucl-court->eucl-hoop
  [[hoop-x hoop-y] [x y]]
  [(- x hoop-x)
   (- y hoop-y)])


(defn client->eucl-court
  [court-id [court-client-width court-client-height] [court-width court-height] [x y]]
  (let [[rect-x rect-y] (bounding-rect-xy court-id)]
    [(-> x (- rect-x) (/ court-client-width) (* court-width))
     (-> y (- rect-y) (/ court-client-height) (* court-height))]))


(defn client->polar-hoop
  [court-id court-client-dimensions court-dimensions hoop-coordinates [x y]]
  (->> [x y]
       (client->eucl-court court-id court-client-dimensions court-dimensions)
       (eucl-court->eucl-hoop hoop-coordinates)
       (eucl-hoop->polar-hoop)))


(def court-dimensions [(* 12 50) (* 12 42)])


(def hoop-coordinates
  (let [[court-width _] court-dimensions]
    [(/ court-width 2) 63]))
