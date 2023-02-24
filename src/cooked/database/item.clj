(ns cooked.database.item
 (:require
    [clojure.java.jdbc :as jdbc]
    [cooked.database :refer [spec]]
    [hugsql-adapter-case.adapters :refer [kebab-adapter]]
    [hugsql.core :as hugsql]))

(def item-sql "cooked/database/sql/item.sql")

(hugsql/def-db-fns item-sql {:adapter (kebab-adapter)})
(hugsql/def-sqlvec-fns item-sql)

(defn user-items [username]
 (jdbc/reducible-query spec (user-items-sqlvec {:account username}) {:fetch-size 30}))

(defn set-keywords! [conn item-id keywords]
 (do (jdbc/delete! conn :item_keyword ["item_id = ?" item-id])
     (doseq [keyword keywords]
            (jdbc/insert! conn :item_keyword {:keyword keyword :item_id item-id}))))

(defn create-item! [user item]
 (let [item-id (str (java.util.UUID/randomUUID))]
      (jdbc/with-db-transaction [conn spec]
                                (create-item conn (merge item {:id item-id :account user}))
                                (set-keywords! conn item-id (:keywords item)))))

(defn log-extraction [user url item]
 (jdbc/insert! spec :extract {:account user :url url :item item}))

(defn- get-item-keywords [item-id]
 (map :keyword
      (item-keywords spec {:item-id item-id})))

(defn get-owner-username [item-id]
 (:account (item-owner spec {:item-id item-id})))

(defn get-item [item-id]
 (let [item (get-item-by-id spec {:item-id item-id})
       item-keywords (when item (get-item-keywords item-id))]
      (when item (assoc item :keywords item-keywords))))

(defn get-by-url [user item-url]
  (get-item-by-url spec {:account user :url item-url}))

(defn update! [item-id item]
 (jdbc/with-db-transaction [conn spec]
                           (jdbc/update! conn :item (select-keys item [:title :description]) ["id = ?" item-id])
                           (set-keywords! conn item-id (:keywords item))))

(defn update-item-image [item-id image-url]
 (jdbc/update! spec :item {:image_url image-url} ["id = ?" item-id]))

(defn delete! [item-id]
 (jdbc/with-db-transaction [conn spec]
                           (jdbc/delete! conn :item ["id = ?" item-id])))

(defn count-item-keyword [user search-query search-keywords]
 (count-account-items spec {:account user :query search-query :keywords search-keywords}))

(defn search [user search-query search-keywords page-limit page]
 (let [offset (* page-limit (- page 1))]
      (query-account-items spec {:account user :query search-query :keywords search-keywords
                                 :limit page-limit :offset offset})))
