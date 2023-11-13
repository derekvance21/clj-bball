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
  (let [dom-rect (.getBoundingClientRect (.getElementById js/document id))
        x (.-left dom-rect)
        y (.-top dom-rect)]
    {:x x
     :width (- (.-right dom-rect) x)
     :y y
     :height (- (.-bottom dom-rect) y)}))


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
  [court-id [court-width court-height] [x y]]
  (let [{rect-x :x rect-y :y rect-width :width rect-height :height} (bounding-rect-xy court-id)]
    [(-> x (- rect-x) (/ rect-width) (* court-width))
     (-> y (- rect-y) (/ rect-height) (* court-height))]))


(defn client->polar-hoop
  [court-id court-dimensions hoop-coordinates [x y]]
  (->> [x y]
       (client->eucl-court court-id court-dimensions)
       (eucl-court->eucl-hoop hoop-coordinates)
       (eucl-hoop->polar-hoop)))


(def court-dimensions [(* 12 50) (* 12 42)])


(def hoop-coordinates
  (let [[court-width _] court-dimensions]
    [(/ court-width 2) 63]))
