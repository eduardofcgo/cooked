(ns cooked.database
 (:require
   [clojure.data.json :as json]
   [clojure.java.jdbc :as jdbc]
   [clojure.pprint :as pprint]
   [clojure.string :as string]
   [config.core :refer [env]]
   [migratus.core :as migratus])
 (:import
   (com.mchange.v2.c3p0 ComboPooledDataSource)
   (org.postgresql.util PGobject)))

(defn- create-pool []
  (let [cpds (doto (ComboPooledDataSource.)
                   (.setJdbcUrl (:connection-uri (:db env)))
                   (.setMaxIdleTimeExcessConnections (* 30 60))
                   (.setMaxIdleTime (* 3 60 60))
                   (.setTestConnectionOnCheckout true)
                   (.setPreferredTestQuery "SELECT 1"))]
       {:datasource cpds}))

(def spec (create-pool))

(extend-protocol jdbc/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [value]
    (doto (PGobject.)
          (.setType "json")
          (.setValue (json/write-str value)))))

(def migration-config {:store :database
                       :migration-dir "migrations/"
                       :db (:db env)})

(defn run-migrations []
 (migratus/migrate migration-config))
