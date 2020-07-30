(ns crudless-todomvc.client
  (:require [crudless-todomvc.type :as type]
            [crudless-todomvc.ui :as ui]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.networking.mock-server-remote :as remote]
            [com.wsscode.common.async-cljs :refer [go-catch <?]]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.connect.graphql2 :as pcg]
            [com.wsscode.pathom.diplomat.http :as p.http]
            [com.wsscode.pathom.diplomat.http.fetch :as p.http.fetch]
            [clojure.string :as str]))

(def indexes (atom {}))

(defn demunger-map-reader
  [{::pcg/keys [demung]
    :keys  [ast query]
    :as    env}]
  (let [entity (p/entity env)
        k (:key ast)]
    (if-let [[_ v] (find entity (pcg/demung-key demung k))]
      (if (sequential? v)
        (if query
          (p/join-seq env v)
          (mapv #(type/coerce k %) v))
        (if (and (map? v) query)
          (p/join v env)
          (type/coerce k v)))
      ::p/continue)))

(def parser-item
  (p/parser {::p/env     {::p/reader [pcg/error-stamper
                                      demunger-map-reader
                                      p/env-placeholder-reader
                                      pcg/gql-ident-reader]}
             ::p/plugins [(p/env-wrap-plugin
                           (fn [env]
                             (-> (merge {::pcg/demung identity} env)
                                 (update ::p/placeholder-prefixes
                                         #(or % #{})))))]}))

(def my-gql
  {::pcg/url "/api/graphql/v1/graphql"
   ::pcg/prefix "crudless-todomvc.type"
   ::pcg/ident-map {}
   ::pcg/mung #(str/replace % \_ \-)
   ::pcg/demung #(str/replace % \- \_)
   ::pcg/parser-item parser-item
   ::p.http/driver p.http.fetch/request-async})

(def parser
  (p/parallel-parser
   {::p/env {::p/reader [p/map-reader
                         pc/parallel-reader
                         pc/open-ident-reader
                         p/env-placeholder-reader]
             ::p/placeholder-prefixes #{">"}
             ::p.http/driver p.http.fetch/request-async}
    ::p/mutate pc/mutate-async
    ::p/plugins [(pc/connect-plugin {::pc/indexes indexes})
                 p/error-handler-plugin
                 p/request-cache-plugin
                 p/trace-plugin]}))

(defonce SPA (atom nil))

(defn mount []
  (reset! SPA (app/mount! @SPA ui/Root "app")))

(defn ^:export init []
  (reset! SPA (app/fulcro-app
                {:started-callback
                 (fn [app]
                   (go-catch
                     (<? (pcg/load-index my-gql indexes))
                     (df/load! app [:component/by-id :todo-list] ui/TodoList)))

                 :remotes
                 {:remote (remote/mock-http-server {:parser (fn [query] (parser {} query))})}}))
  (mount))
