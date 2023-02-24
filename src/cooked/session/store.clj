(ns cooked.session.store
 (:require
   [clojure.java.jdbc :as jdbc]
   [ring.middleware.session.store :refer [SessionStore]]
   [taoensso.nippy :as nippy]))

(def serialize nippy/freeze)

(defn deserialize [session]
 (when session
       (nippy/thaw session)))

(def table :session_store)

(defn read-session-jdbc [key conn]
 (-> (jdbc/query conn [(str "select value from " (name table) " where session_id = ?") key])
     first
     :value
     deserialize))

(defn insert-session-jdbc! [session conn]
  (let [new-session-key (java.util.UUID/randomUUID)
        new-jdbc-session {:session_id new-session-key :value (serialize session)}]

       (do (jdbc/insert! conn table new-jdbc-session)
           new-session-key)))

(defn update-session-jdbc! [key session conn]
  (let [new-jdbc-session {:session_id key :value (serialize session)}

        updated (jdbc/update! conn table new-jdbc-session ["session_id = ?" key])
        unchanged (zero? (first updated))
        session-exists (not unchanged)]

        (do (when-not session-exists
                      (jdbc/insert! conn table new-jdbc-session))
            key)))

(defn delete-session-jdbc! [key conn]
 (jdbc/delete! conn table ["session_id = ?" key]))

(deftype JdbcStore [datasource]
  SessionStore

  (read-session
    [_ key]
    (jdbc/with-db-transaction [conn datasource] (read-session-jdbc key conn)))

  (write-session
    [_ key value]
    (if key
        (jdbc/with-db-transaction [conn datasource] (update-session-jdbc! key value conn))
        (jdbc/with-db-transaction [conn datasource] (insert-session-jdbc! value conn))))

  (delete-session
    [_ key]
    (jdbc/with-db-transaction [conn datasource] (delete-session-jdbc! key conn))
    nil))

(defn jdbc-store [datasource]
 (JdbcStore. datasource))
