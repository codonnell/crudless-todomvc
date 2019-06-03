(ns crudless-todomvc.client
  (:require [crudless-todomvc.type :as type]
            [crudless-todomvc.type.todo :as todo]
            [crudless-todomvc.ui :as ui]
            [fulcro.client :as fc]
            [fulcro.client.data-fetch :as df]
            [fulcro.client.dom :as dom]
            [fulcro.client.primitives :as fp]
            [fulcro.incubator.dynamic-routing :as dr]
            [com.wsscode.common.async-cljs :refer [go-catch <?]]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.connect.graphql2 :as pcg]
            [com.wsscode.pathom.diplomat.http :as p.http]
            [com.wsscode.pathom.diplomat.http.fetch :as p.http.fetch]
            [com.wsscode.pathom.fulcro.network :as pfn]
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

(pc/defresolver index-explorer [env _]
  {::pc/input  #{:com.wsscode.pathom.viz.index-explorer/id}
   ::pc/output [:com.wsscode.pathom.viz.index-explorer/index]}
  {:com.wsscode.pathom.viz.index-explorer/index
   (get env ::pc/indexes)})

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
    ::p/plugins [(pc/connect-plugin {::pc/indexes indexes
                                     ::pc/register [index-explorer]})
                 p/error-handler-plugin
                 p/request-cache-plugin
                 p/trace-plugin]}))

(defonce SPA (atom nil))

(defn mount []
  (reset! SPA (fc/mount @SPA ui/Root "app")))

(defn ^:export init []
  (reset! SPA (fc/new-fulcro-client
               :started-callback
               (fn [app]
                 (go-catch
                  (<? (pcg/load-index my-gql indexes))
                  (df/load app [:component/by-id :todo-list] ui/TodoList)))

               :networking
               {:remote (-> parser
                            pfn/pathom-remote
                            pfn/trace-remote)}))
  (mount))
