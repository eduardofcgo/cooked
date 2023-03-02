(ns cooked.app
 (:require
   [buddy.hashers :as hashers]
   [clj-http.client :as client]
   [clojure.pprint :as pprint]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [config.core :refer [env]]
   [cooked.database.item :as db-item]
   [cooked.database.user :as db-user]
   [cooked.extract :as extract]
   [taoensso.timbre :as timbre :refer [info error]])
 (:import
   (java.util.zip ZipOutputStream ZipEntry)
   (org.apache.commons.lang3 StringUtils)
   (org.apache.commons.validator.routines UrlValidator)))

(def http-config (get-in env [:scrape :client]))

(defn- download-page [url]
 (client/get url http-config))

(defn valid-url? [url]
 (-> (UrlValidator.)
     (.isValid url)))

(defn extract-new-item [user url]
 (try (let [page (download-page url)
            body (page :body)
            item (extract/extract-from-page body url)]
            (info "Extracted" url item)
            (db-item/log-extraction user url item)
            item)
      (catch Exception e (do (error e "Error extracting page" url)
                             (db-item/log-extraction user url nil)
                             (extract/extract-from-url url)))))

(defn extract-new-item-guest [url]
 (extract-new-item nil url))

(defn coerse-keyword-valid [keyword]
 (let [clean-keyword (extract/keywordize keyword)]
      (when (extract/valid-keyword? clean-keyword) clean-keyword)))

(defn delete-item [item-id]
 (db-item/delete! item-id))

(defn get-item-by-url [user item-url]
 (db-item/get-by-url user item-url))

(defn extra-searchable-content [item]
 (string/join ", " (item :keywords)))

(defn create-item! [user item]
 (let [already-exists? (some? (get-item-by-url user (:url item)))
       searchable-item (assoc item :extra (extra-searchable-content item))]
      (when-not already-exists? (db-item/create-item! user searchable-item))))

(defn get-item [item-id]
 (db-item/get-item item-id))

(defn get-user [username]
 (db-user/get-user username))

(defn count-item-keyword [user query keywords]
 (db-item/count-item-keyword user query keywords))

(defn update-item! [id item]
 (let [searchable-item (assoc item :extra (extra-searchable-content item))]
      (db-item/update! id searchable-item)))

(def paginator-item-limit 30)

(defn item-paginator [user query keywords]
 (let [get-page-items (partial db-item/search user query keywords paginator-item-limit)]
      (fn [page] {:items (get-page-items page)

                  :page page

                  :next-page (let [next-page (+ page 1)
                                   next-page-items (get-page-items next-page)]
                                  (when (not-empty next-page-items) next-page))

                  :previous-page (when (> page 1) (- page 1))})))

(defn edit-authorized? [user item-id]
 (= user (db-item/get-owner-username item-id)))

(defn view-authorized? [username item-id]
 (let [owner-username (db-item/get-owner-username item-id)
       user-owner (db-user/get-user owner-username)]
      (or (not (:private user-owner)) (edit-authorized? username item-id))))

(defn authenticate? [username password]
 (let [user (db-user/get-user username)]
      (when user (:valid (hashers/verify password (user :password))))))

(defn create-user! [username password]
 (db-user/create-user! username (hashers/derive password {:alg :bcrypt+blake2b-512})))

(def username-pattern #"^[a-zA-Z0-9]([._-](?![._-])|[a-zA-Z0-9]){3,16}[a-zA-Z0-9]$")

(defn valid-username? [username]
 (some? (re-matches username-pattern username)))

(defn set-user-private [username is-private]
 (db-user/update-user! username {:private is-private}))

(defn refresh-image [user item-id]
 (let [item (get-item item-id)
       item-url (item :url)
       updated-item (extract-new-item user item-url)
       updated-item-image (updated-item :image-url)]

       (if (some? updated-item-image)
           (do (info "Updating image for item" item-id item-url)
               (db-item/update-item-image item-id updated-item-image))
           (error "Unable to find new image for item" item-id item-url))))

(defn- export-item-text [item]
 (string/join "\n"
              (map item [:title :url :description])))

(defn- export-item-zip [zip-out item]
 (let [file-name (str (item :title) ".txt")
       file-content (export-item-text item)]
      (doto zip-out
            (.putNextEntry (ZipEntry. file-name))
            (.write (.getBytes file-content))
            (.closeEntry)
            (.flush))))

(defn export-items-zip [username out]
 (let [items (db-item/user-items username)
       zip-out (ZipOutputStream. out)]
      (transduce (map (partial export-item-zip zip-out)) (constantly nil) items)
      zip-out))
