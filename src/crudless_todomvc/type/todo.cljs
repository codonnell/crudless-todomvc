(ns crudless-todomvc.type.todo
  (:require [crudless-todomvc.type :as type]))

(defmethod type/coerce ::id [_ v] (uuid v))
