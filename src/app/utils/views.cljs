(ns app.utils.views
  (:require
   [re-frame.core :as re-frame]
   [clojure.string :as string]
   [app.utils :refer [court-dimensions]]))


(def <sub  (comp deref re-frame/subscribe))


;; id has to be keyword
;; option for just vector w/o id
(defn dropdown
  [& {:keys [choices model on-change]}]
  [:select {:on-change #(on-change (-> % .-target .-value keyword))
            :value model}
   (for [{:keys [id label]} choices]
     [:option {:key id :value id}
      label])])


(defn button
  [{:keys [disabled? selected? on-click class]} label]
  [:button.font-semibold.rounded-full.border.border-blue-500
   {:type "button"
    :on-click on-click
    :disabled disabled?
    :class [class
            (cond
              selected? "bg-blue-500 text-white"
              disabled? "bg-transparent text-blue-700 opacity-50 cursor-not-allowed"
              :else "bg-transparent text-blue-700 hover:bg-blue-500 hover:text-white hover:border-transparent")]}
   label])


(defn court
  [{:keys [id on-click on-context-menu class]
    :or {class "w-full" id (gensym "court-")}} & children]
  (let [[court-width court-height] court-dimensions
        line-width 2]
    [:svg
     {:xmlns "http://www.w3.org/2000/svg"
      :version "1.1"
      :class class
      :style {:min-width "200px"}
      :view-box (string/join " " [(- line-width) (- line-width) (+ court-width (* 2 line-width)) (+ court-height (* 2 line-width))])
      :on-click on-click
      :on-context-menu on-context-menu}
     (for [child children]
       (with-meta child {:key (gensym "key-")}))
     [:rect {:id id :x 0 :y 0 :width court-width :height court-height :fill (or "none" "#dfbb85")}]
     [:g {:id "lines" :fill "none" :stroke-width line-width :stroke "black" :stroke-linecap "butt" :stroke-linejoin "miter"}
      [:rect {:x (- (/ line-width 2)) :y (- (/ line-width 2))
              :width (+ line-width court-width) :height (+ line-width court-height)}]
      [:path {:d "M 63 0 L 63 63 a 237 237 0 0 0 474 0 l 0 -63"}] ;; 3 point arc
      [:circle {:cx 300 :cy 63 :r 9}] ;; hoop
      [:line {:x1 269 :y1 48 :x2 331 :y2 48}] ;; backboard
      [:polyline {:points "228 0 228 228 370 228 370 0"}] ;; paint
      [:path {:d "M 228 228 A 71 71 0 0 0 370 228"}] ;; top of key
      [:path {:d "M 228 504 a 71 71 0 0 1 142 0"}] ;; halfcourt circle
      [:path {:d "M 227 94 l -8 0 m 8 36 l -8 0 m 8 36 l -8 0 m 8 36 l -8 0"}] ; left lane markings
      [:path {:d "M 371 94 l 8 0 m -8 36 l 8 0 m -8 36 l 8 0 m -8 36 l 8 0"}] ; right lane markings
      ]]))
