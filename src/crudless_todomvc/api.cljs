(ns crudless-todomvc.api
  (:require
   [com.fulcrologic.fulcro.mutations :as mut :refer [defmutation]]
   [edn-query-language.core :as eql]
   [crudless-todomvc.type :as type]
   [crudless-todomvc.type.todo :as todo]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client-side API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-item-to-list*
  "Add an item's ident onto the end of the given list."
  [state-map item-id]
  (update-in state-map [:component/by-id :todo-list ::type/todos] (fnil conj []) [::todo/id item-id]))

(defn create-item*
  "Create a new todo item and insert it into the todo item table."
  [state-map id label]
  (assoc-in state-map [::todo/id id] {::todo/id id ::todo/label label ::todo/complete false}))

(defn set-item-checked*
  [state-map id checked?]
  (assoc-in state-map [::todo/id id ::todo/complete] checked?))

(defn clear-list-input-field*
  "Clear the main input field of the todo list"
  [state-map]
  (assoc-in state-map [:component/by-id :todo-list :ui/new-item-text] ""))

(defmutation todo-new-item [{:keys [id label]}]
  (action [{:keys [state ast]}]
    (js/console.info {:id id :label label})
    (swap! state #(-> %
                    (create-item* id label)
                    (add-item-to-list* id)
                    (clear-list-input-field*))))
  (remote [_]
    (eql/query->ast1 `[{(type/insert-todos-one {:object ~{:id (str id) :label label}})
                        [::todo/id]}])))

(defn- update-todo-by-id-ast [id m]
  (eql/query->ast1 `[{(type/update-todos-by-pk {:pk-columns {:id ~(str id)}
                                                :_set ~m})
                      [::todo/id]}]))

(defmutation todo-check [{:keys [id]}]
  (action [{:keys [state]}]
    (swap! state set-item-checked* id true))
  (remote [_]
    (update-todo-by-id-ast id {:complete true})))

(defmutation todo-uncheck [{:keys [id]}]
  (action [{:keys [state]}]
    (swap! state set-item-checked* id false))
  (remote [_]
    (update-todo-by-id-ast id {:complete false})))

(defn set-item-label*
  "Set the given item's label"
  [state-map id label]
  (assoc-in state-map [::todo/id id ::todo/label] label))

(defmutation commit-label-change
  [{:keys [id label]}]
  (action [{:keys [state]}]
    (swap! state set-item-label* id label))
  (remote [_]
    (update-todo-by-id-ast id {:label label})))

(defn remove-from-idents
  "Given a vector of idents and an id, return a vector of idents that have none that use that ID for their second (id) element."
  [vec-of-idents id]
  (filterv (fn [ident] (not= id (second ident))) vec-of-idents))

(defmutation todo-delete-item [{:keys [id]}]
  (action [{:keys [state]}]
    (swap! state #(-> %
                    (update-in [:component/by-id :todo-list ::type/todos] remove-from-idents id)
                    (update ::todo/id dissoc id))))
  (remote [_]
    (eql/query->ast1 `[{(type/delete-todos-by-pk {:id ~(str id)})
                        [::todo/id]}])))

(defn on-all-items-in-list
  [state-map xform & args]
  (let [item-idents (get-in state-map [:component/by-id :todo-list ::type/todos])]
    (reduce (fn [s idt]
              (let [id (second idt)]
                (apply xform s id args)))
      state-map item-idents)))

(defmutation todo-check-all [_]
  (action [{:keys [state]}]
    (swap! state on-all-items-in-list set-item-checked* true))
  (remote [_]
    (eql/query->ast1 `[{(type/update-todos {:where {:id {:_is_null false}}
                                            :_set {:complete true}})
                        [::type/affected-rows]}])))

(defmutation todo-uncheck-all [_]
  (action [{:keys [state]}]
    (swap! state on-all-items-in-list set-item-checked* false))
  (remote [_]
    (eql/query->ast1 `[{(type/update-todos {:where {:id {:_is_null false}}
                                            :_set {:complete false}})
                        [::type/affected-rows]}])))

(defmutation todo-clear-complete [_]
  (action [{:keys [state]}]
    (let [is-complete? (fn [item-ident] (get-in @state (conj item-ident ::todo/complete)))]
      (swap! state update-in [:component/by-id :todo-list ::type/todos]
        (fn [todos] (vec (remove (fn [ident] (is-complete? ident)) todos))))))
  (remote [_]
    (eql/query->ast1 `[{(type/delete-todos {:where {:complete {:_eq true}}})
                        [::type/affected-rows]}])))

(defmutation todo-filter
  [{:keys [filter]}]
  (action [{:keys [state]}]
    (swap! state assoc-in [:component/by-id :todo-list :ui/filter] filter)))
