(ns cooked.database.user
 (:require
    [clojure.java.jdbc :as jdbc]
    [cooked.database :refer [spec]]
    [hugsql-adapter-case.adapters :refer [kebab-adapter]]
    [hugsql.core :as hugsql]))

(hugsql/def-db-fns "cooked/database/sql/user.sql" {:adapter (kebab-adapter)})

(defn create-user! [username password]
 (jdbc/insert! spec :account {:username username :password password}))

(defn get-user
 ([username] (account-with-username spec {:username username}))
 ([username password] (account-with-username-password spec {:username username :password password})))

(defn update-user! [username user]
 (jdbc/update! spec :account user ["username = ?" username]))
