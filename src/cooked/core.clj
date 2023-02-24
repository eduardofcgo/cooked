(ns cooked.core
  (:require [buddy.auth.backends :as auth-backends]
            [buddy.auth.middleware :as auth-middleware]
            [clojure.pprint :as pprint]
            [config.core :refer [env]]
            [cooked.controllers :as controllers]
            [cooked.database :as database]
            [cooked.routes :refer [routes]]
            [cooked.session.store :refer [jdbc-store]]
            [cooked.views :as views]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.content-type :as content-type]
            [ring.middleware.keyword-params :as keyword-params]
            [ring.middleware.params :as params]
            [ring.middleware.resource :as resource]
            [ring.middleware.session :as session]
            [ring.util.response :as response])
  (:gen-class))

(def session-store-datasource database/spec)
(def session-store (jdbc-store session-store-datasource))

(def auth-backend
 (auth-backends/session {:unauthorized-handler controllers/unauthorized-handler}))

(def default-content-type "text/html")

(def app
 (-> routes
     (resource/wrap-resource "public")
     (auth-middleware/wrap-authentication auth-backend)
     (auth-middleware/wrap-authorization auth-backend)
     (session/wrap-session {:store session-store})
     (keyword-params/wrap-keyword-params)
     (params/wrap-params)
     (content-type/wrap-content-type {:mime-types {nil default-content-type}})))

(defn -main
  [& args]
  (cond (= args ["migrate"]) (database/run-migrations)
        (= args ["serve"]) (jetty/run-jetty app {:port (env :port)})
        :else (do (println "Valid options: migrate, serve") (System/exit 1))))
