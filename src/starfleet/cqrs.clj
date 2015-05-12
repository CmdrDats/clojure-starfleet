(ns starfleet.cqrs
  (:require
    [clojure.tools.logging :as log]
    [clojure.core.async :as a]
    [datomic.api :as d]
    [datomic-schema.schema :refer [schema fields] :as ds]

    [taoensso.faraday :as far]
    [taoensso.nippy :as nippy]

    [cqrs-server.cqrs :as cqrs]
    [cqrs-server.util :as cqrsutil]
    [cqrs-server.datomic :as datomic]
    [cqrs-server.dynamo :as dynamo]
    [cqrs-server.async :as async]
    [cqrs-server.simple :as simple]

    [environ.core :refer [env]]
    [schema.coerce :as coerce]

    [com.stuartsierra.component :as component]))

(defmacro build-env [var]
  (println "Baking in" var ":" (env var))
  (env var))

(def git-sha (build-env :git-commit))

(defn catalog-map [streams dynamo-cred datomic-uri]
  {:command/in-queue (async/stream :input (:command-stream streams))
   :command/process (datomic/command-processor datomic-uri)
   :event/out-queue (async/stream :output (:event-stream streams))
   :event/in-queue (async/stream :input (:event-stream streams))
   :event/store (dynamo/catalog dynamo-cred)
   :event/aggregator (datomic/aggregate-stream datomic-uri)
   :command/feedback (async/stream :output (:feedback-stream streams))
   :cqrs/error (async/stream :fn (:error-stream streams))})

(def feedback (atom {}))

(defn feedback-promise [cmd timeout]
  (let [a (a/chan 1)
        f (future
            (let [r (first (a/alts!! [a (a/timeout timeout)]))]
              (swap! feedback dissoc (:cid cmd))
              r))]
    (swap! feedback assoc (:cid cmd) a)
    f))

(defn setup-feedback [chan]
  (a/go-loop []
    (if-let [m (a/<! chan)]
      (do
        (log/info "!!! FEEDBACK !!! " m " - " @feedback)
        (when-let [c (get @feedback (:cid m))]
          (a/>! c m))
        (recur)))))


(defn setup-env [{:keys [db-schema dynamo-cred datomic]}]
  (log/info "Setting up CQRS server")
  (try
    (log/info "Setting up dynamodb table:" (:tablename dynamo-cred))
    (dynamo/table-setup dynamo-cred)
    (catch Exception e nil))

  (log/info "Transacting schema:" (map :db/ident (ds/generate-schema db-schema)))
  (let [r (d/transact
            (d/connect datomic)
            (ds/generate-schema (concat datomic/db-schema db-schema)))]
    (log/info "Result of transact:" r))

  (let [feedback-chan (a/chan 10)
        streams
        {:command-stream (atom (a/chan 10))
         :event-stream (atom (a/chan 10))
         :feedback-stream (atom feedback-chan)
         :error-stream (atom feedback-chan)}]

    (setup-feedback feedback-chan)
    (let [catalog (catalog-map streams dynamo-cred datomic)
          setup (cqrs/setup (java.util.UUID/randomUUID) catalog)]
      {:cqrs/streams streams
       :cqrs/shutdown #(doseq [[_ c] streams] (a/close! @c))
       :cqrs/simple (simple/start setup)})))

(defn send-command [{{:keys [type data] :as msg} :msg {:keys [datomic cqrs/streams]} :ctx :as req}]
  (let [t (d/basis-t (d/db (d/connect datomic)))
        type (keyword type)
        _ (log/info "Sending command:" type "at" t "on" git-sha)
        cmd (cqrs/command git-sha t type data)
        p (feedback-promise cmd 500)]
    (a/>!! (-> streams :command-stream deref) cmd)
    @p))


(def module
  {:db-schema
   [(schema
      base
      (fields
        [uuid :uuid :unique-identity]
        [dateadded :instant]))]})


(defrecord Cqrs [db-schema dynamo-cred datomic]
  component/Lifecycle
  (start [component]
    (let [env (setup-env {:db-schema db-schema :dynamo-cred dynamo-cred :datomic datomic})]
      (assoc component :env env)))

  (stop [component]
    ((-> component :env :cqrs/shutdown))))

(defn new-cqrs [db-schema dynamo-cred datomic]
  (Cqrs. db-schema dynamo-cred datomic))
