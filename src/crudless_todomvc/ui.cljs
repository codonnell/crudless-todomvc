(ns crudless-todomvc.ui
  (:require
   [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
   [com.fulcrologic.fulcro.dom :as dom]
   [com.fulcrologic.fulcro.mutations :as mut]
   [crudless-todomvc.type :as type]
   [crudless-todomvc.type.todo :as todo]
   [crudless-todomvc.api :as api]
   [clojure.string :as str]))

(defn is-enter? [evt] (= 13 (.-keyCode evt)))
(defn is-escape? [evt] (= 27 (.-keyCode evt)))

(defn trim-text
  "Returns text without surrounding whitespace if not empty, otherwise nil"
  [text]
  (let [trimmed-text (str/trim text)]
    (when-not (empty? trimmed-text)
      trimmed-text)))

(defsc TodoItem [this
                 {:ui/keys   [editing edit-text]
                  ::todo/keys [id label complete] :or {complete false} :as props}
                 {:keys [delete-item check uncheck] :as computed}]
  {:query              (fn [] [::todo/id ::todo/label ::todo/complete :ui/editing :ui/edit-text])
   :ident              [::todo/id ::todo/id]
   :componentDidUpdate (fn [this prev-props _]
                         ;; Code adapted from React TodoMVC implementation
                         (when (and (not (:editing prev-props)) (:editing (comp/props this)))
                           (let [input-field        (js/ReactDOM.findDOMNode (.. this -refs -edit_field))
                                 input-field-length (when input-field (.. input-field -value -length))]
                             (when input-field
                               (.focus input-field)
                               (.setSelectionRange input-field input-field-length input-field-length)))))}
  (let [submit-edit (fn [evt]
                      (if-let [trimmed-text (trim-text (.. evt -target -value))]
                        (do
                          (comp/transact! this `[(api/commit-label-change ~{:id id :label trimmed-text})])
                          (mut/set-string! this :ui/edit-text :value trimmed-text)
                          (mut/toggle! this :ui/editing))
                        (delete-item id)))]

    (dom/li {:classes [(when complete (str "completed")) (when editing (str " editing"))]}
            (dom/div :.view {}
                     (dom/input {:type      "checkbox"
                                 :className "toggle"
                                 :checked   (boolean complete)
                                 :onChange  #(if complete (uncheck id) (check id))})
                     (dom/label {:onDoubleClick (fn []
                                                  (mut/toggle! this :ui/editing)
                                                  (mut/set-string! this :ui/edit-text :value label))} label)
                     (dom/button :.destroy {:onClick #(delete-item id)}))
            (dom/input {:ref       "edit_field"
                        :className "edit"
                        :value     (or edit-text "")
                        :onChange  #(mut/set-string! this :ui/edit-text :event %)
                        :onKeyDown #(cond
                                      (is-enter? %) (submit-edit %)
                                      (is-escape? %) (do (mut/set-string! this :ui/edit-text :value label)
                                                         (mut/toggle! this :ui/editing)))
                        :onBlur    #(when editing (submit-edit %))}))))

(def ui-todo-item (comp/factory TodoItem {:keyfn ::todo/id}))

(defn header [component title]
  (let [{:ui/keys [new-item-text]} (comp/props component)]
    (dom/header :.header {}
                (dom/h1 {} title)
                (dom/input {:value       (or new-item-text "")
                            :className   "new-todo"
                            :onKeyDown   (fn [evt]
                                           (when (is-enter? evt)
                                             (when-let [trimmed-text (trim-text (.. evt -target -value))]
                                               (comp/transact! component `[(api/todo-new-item ~{:id (random-uuid)
                                                                                              :label    trimmed-text})]))))
                            :onChange    (fn [evt] (mut/set-string! component :ui/new-item-text :event evt))
                            :placeholder "What needs to be done?"
                            :autoFocus   true}))))

(defn filter-footer [component num-todos num-completed]
  (let [{:ui/keys [filter]} (comp/props component)
        num-remaining (- num-todos num-completed)]

    (dom/footer :.footer {}
                (dom/span :.todo-count {}
                          (dom/strong (str num-remaining " left")))
                (dom/ul :.filters {}
                        (dom/li {}
                                (dom/a {:className (when (or (nil? filter) (= :list.filter/none filter)) "selected")
                                        :href      "#"
                                        :onClick #(comp/transact! component `[(api/todo-filter {:filter :list.filter/none})])} "All"))
                        (dom/li {}
                                (dom/a {:className (when (= :list.filter/active filter) "selected")
                                        :href      "#/active"
                                        :onClick #(comp/transact! component `[(api/todo-filter {:filter :list.filter/active})])} "Active"))
                        (dom/li {}
                                (dom/a {:className (when (= :list.filter/completed filter) "selected")
                                        :href      "#/completed"
                                        :onClick #(comp/transact! component `[(api/todo-filter {:filter :list.filter/completed})])} "Completed")))
                (when (pos? num-completed)
                  (dom/button {:className "clear-completed"
                               :onClick   #(comp/transact! component `[(api/todo-clear-complete {})])} "Clear Completed")))))

(defn footer-info []
  (dom/footer :.info {}
              (dom/p {} "Double-click to edit a todo")
              (dom/p {} "Adapted from "
                     (dom/a {:href   "http://www.fulcrologic.com"
                             :target "_blank"} "Fulcrologic, LLC"))
              (dom/p {} "Part of "
                     (dom/a {:href   "http://todomvc.com"
                             :target "_blank"} "TodoMVC"))))

(defsc TodoList [this {:ui/keys [filter title] ::type/keys [todos] :as props}]
  {:initial-state {:ui/new-item-text "" ::type/todos [] :ui/title "main" :ui/filter :list.filter/none}
   :ident         (fn [] [:component/by-id :todo-list])
   :query         [:ui/new-item-text {::type/todos (comp/get-query TodoItem)} :ui/title :ui/filter]}
  (let [num-todos       (count todos)
        completed-todos (filterv ::todo/complete todos)
        num-completed   (count completed-todos)
        all-completed?  (= num-completed num-todos)
        filtered-todos  (case filter
                          :list.filter/active (into [] (remove ::todo/complete) todos)
                          :list.filter/completed completed-todos
                          todos)
        delete-item     (fn [item-id] (comp/transact! this `[(api/todo-delete-item ~{:id item-id})]))
        check           (fn [item-id] (comp/transact! this `[(api/todo-check ~{:id item-id})]))
        uncheck         (fn [item-id] (comp/transact! this `[(api/todo-uncheck ~{:id item-id})]))]
    (dom/div {}
             (dom/section :.todoapp {}
                          (header this title)
                          (when (pos? num-todos)
                            (dom/div {}
                                     (dom/section :.main {}
                                                  (dom/input {:type      "checkbox"
                                                              :className "toggle-all"
                                                              :checked   all-completed?
                                                              :onClick   (fn [] (if all-completed?
                                                                                  (comp/transact! this `[(api/todo-uncheck-all {})])
                                                                                  (comp/transact! this `[(api/todo-check-all {})])))})
                                                  (dom/label {:htmlFor "toggle-all"} "Mark all as complete")
                                                  (dom/ul :.todo-list {}
                                                          (mapv #(ui-todo-item (comp/computed %
                                                                                            {:delete-item delete-item
                                                                                             :check       check
                                                                                             :uncheck     uncheck})) filtered-todos)))
                                     (filter-footer this num-todos num-completed))))
             (footer-info))))

(def ui-todo-list (comp/factory TodoList))

(defsc Root [this {:keys [todo-list] :as props}]
  {:initial-state (fn [p] {:todo-list (comp/get-initial-state TodoList {})})
   :query         [{:todo-list (comp/get-query TodoList)}]}
  (dom/div {} (ui-todo-list todo-list)))
