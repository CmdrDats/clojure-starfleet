(ns starfleet.command
  (:require
    [environ.core :refer [env]]
    [clojure.tools.logging :as log]
    [system.core :refer [defsystem]]
    (system.components
      [http-kit :refer [new-web-server]]
      [repl-server :refer [new-repl-server]]
      [sente :refer [new-channel-sockets]]
      [datomic :refer [new-datomic-db]])
    [reloaded.repl :refer [system]]
    [ring.middleware.resource :as resource]
    [ring.middleware.reload :as reload]
    [ring.middleware.defaults :as defaults]
    [compojure.core :as c]
    [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
    [starfleet.cqrs :as cqrs])
  (:gen-class))



(defn home [_]
  "Hellosh")

(defonce messages (atom []))

(defn handle-message [{:keys [event send-fn client-id] :as socket}]
  (swap! messages conj event)
  (log/info (str client-id "::" event)))

(c/defroutes
  approutes
  (c/GET "/testing" [] home)
  (c/GET  "/chsk" req ((:ring-ajax-get-or-ws-handshake (:sente system)) req))
  (c/POST "/chsk" req ((:ring-ajax-post (:sente system)) req)))

(def app
  (->
    #'approutes
    (reload/wrap-reload)
    (defaults/wrap-defaults defaults/site-defaults)
    (resource/wrap-resource "resources/public")
    ))

(def modules
  [cqrs/module])

(def dynamo-cred
  {:access-key (env :aws-access-key)
   :secret-key (env :aws-secret-key)
   :endpoint   (env :dynamo-endpoint)
   :tablename  (env :dynamo-tablename)})

(defsystem
  starfleet-system
  [:sente (new-channel-sockets #'handle-message sente-web-server-adapter)
   :web (new-web-server (Integer/parseInt (env :web-port)) #'app)
   :repl-server (new-repl-server (Integer/parseInt (env :repl-port)))
   :datomic (new-datomic-db (env :datomic-uri))
   :cqrs (cqrs/new-cqrs (mapcat :db-schema modules) dynamo-cred (env :datomic-uri))])

(defn -main [& args]
  (reloaded.repl/set-init! starfleet-system)
  (reloaded.repl/go)
  (log/info "Started HTTP server on port" (env :web-port) ", nrepl started on" (env :repl-port)))
