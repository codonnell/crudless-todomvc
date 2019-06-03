(ns crudless-todomvc.type)

(defmulti coerce (fn [k v] k))

(defmethod coerce :default [k v] v)
