(ns cooked.controllers
  (:require [buddy.auth :as auth]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [cooked.app :as app]
            [cooked.views :as views]
            [ring.util.codec :as codec]
            [ring.util.io :as ring-io]
            [ring.util.response :as response])
  (:import
   (org.apache.commons.lang3 StringUtils)))

(defn unauthorized-handler
  [request metadata]
  (if (auth/authenticated? request)
      {:status 403 :body "Forbidden"}
      {:status 401 :body "Unauthorized"}))

(defn- coerse-keywords-valid [keywords]
 (filter some? (map app/coerse-keyword-valid keywords)))

(defn- parse-keywords [params]
 (let [keywords-comma-separated (:keywords params)
       keywords (string/split keywords-comma-separated #",")]
      (coerse-keywords-valid keywords)))

(defn parse-item [params]
 (let [strip-null #(StringUtils/stripToNull %)]
      (-> params
          (update :title strip-null)
          (update :url strip-null)
          (update :image-url strip-null)
          (assoc :keywords (parse-keywords params)))))

(defn delete-item [item-id {session :session}]
 (let [user (:identity session)]
      (if-not (app/edit-authorized? user item-id)
              (auth/throw-unauthorized))
              (do (app/delete-item item-id)
                  (response/redirect "/"))))

(defn save-item [{params :params session :session}]
 (let [user (:identity session)
       item (parse-item params)]
       (if (nil? user) (auth/throw-unauthorized)
           (do (app/create-item! user item)
               (response/redirect "/")))))

(defn update-item [item-id {session :session params :params}]
 (let [user (:identity session)
      should-refresh-image (-> (params :refresh-image) (= "true"))]
      (if-not (app/edit-authorized? user item-id)
              (auth/throw-unauthorized)
              (let [keywords (parse-keywords params)
                    item (parse-item params)]
                   (do (app/update-item! item-id item)
                       (when should-refresh-image (app/refresh-image user item-id))
                       (response/redirect "/"))))))

(defn new-item-page [url {:keys [session uri query-params] :as request}]
 (cond (not (app/valid-url? url)) (response/bad-request "Invalid URL")
       (not (auth/authenticated? request)) (-> (response/response
                                                 (views/new-item-page (app/extract-new-item-guest url) nil))
                                               (assoc-in [:session :redirect-after]
                                                         (str uri "?" (codec/form-encode query-params))))
       :else (let [user (:identity session)
                   item (app/get-item-by-url user url)]
                  (if (some? item)
                      (response/bad-request (views/item-already-exists item))
                      (views/new-item-page (app/extract-new-item user url) user)))))

(defn item-page [item-id {session :session}]
 (let [user (:identity session)
       item (app/get-item item-id)]
      (if (nil? item)
          (response/not-found "Not found")
          (cond (app/edit-authorized? user item-id) (views/edit-item-page item-id item)
                (app/view-authorized? user item-id) (views/view-item-page item-id item)
                :else (auth/throw-unauthorized)))))

(defn- ensure-url-http [url]
 (if-not (string/starts-with? url "http")
         (str "http://" url)
         url))

(defn new-item-shortcut [request]
 (let [item-path (get-in request [:params :*])
       query-string (:query-string request)
       item-url (if-not (empty? query-string)
                        (str item-path "?" query-string)
                        item-path)
       item-url-http (ensure-url-http item-url)
       invalid-url (not (app/valid-url? item-url-http))]
      (if invalid-url
          (response/bad-request "Invalid URL")
          (response/redirect (str "/new?" (codec/form-encode {:url item-url-http}))))))

(defn user [request]
 (if-not (auth/authenticated? request)
         (views/login (:query-params request))
         (let [username (get-in request [:session :identity])
               user (app/get-user username)]
              (views/user user))))

(defn delete-item [item-id {session :session}]
 (let [user (:identity session)]
      (if-not (app/edit-authorized? user item-id)
              (auth/throw-unauthorized))
              (do (app/delete-item item-id)
                  (response/redirect "/"))))

(defn update-user [{:keys [form-params session] :as request}]
 (if-not (auth/authenticated? request)
         (auth/throw-unauthorized)
         (let [username (:identity session)
               {:strs [private]} form-params
               is-private (= private "true")]
              (do (app/set-user-private username is-private)
                  (response/redirect "/")))))

(defn authenticate [{:keys [form-params session] :as request}]
 (let [{:strs [username password]} form-params]
      (if-not (app/authenticate? username password)
              (response/bad-request "Invalid authentication")
              (-> (response/redirect (get session :redirect-after "/"))
                  (assoc-in [:session :identity] username)))))

(defn user-register [request]
 (views/register))

(defn register-user [{:keys [form-params session]}]
 (let [{:strs [username password password-confirm]} form-params
       clean-username (StringUtils/stripToNull username)]
      (cond (some? (app/get-user clean-username)) (response/bad-request "Username already exists")
            (not (app/valid-username? clean-username)) (response/bad-request "Invalid username")
            (not= password password-confirm) (response/bad-request "Passwords do not match")
            :else (do (app/create-user! clean-username password)
                      (-> (response/redirect (get session :redirect-after "/"))
                          (assoc-in [:session :identity] clean-username))))))

(defn logout [request]
 (-> (response/redirect "/")
     (assoc :session {})))

(defn export [request]
 (if-not (auth/authenticated? request)
         (auth/throw-unauthorized)
         (let [username (get-in request [:session :identity])]
              {:headers {"Content-Type" "application/zip, application/octet-stream"}
               :body (ring-io/piped-input-stream
                       (fn [out] (with-open [zip-out (app/export-items-zip username out)]
                                            (.flush zip-out)
                                            (.flush out)
                                            (.finish zip-out))))})))

(defn- ensure-multiple-value-param [param]
 (let [param-vec (if (string? param) [param] param)]
      (filter not-empty param-vec)))

(defn user-page [page-owner-username query keywords page {session :session}]
 (let [page-owner (app/get-user page-owner-username)
       is-viewing-owner (= (:identity session) page-owner-username)]
       (if (and (:private page-owner) (not is-viewing-owner))
           (auth/throw-unauthorized)
           (let [clean-query (StringUtils/stripToNull query)
                 page-number (when-let [stripped-page (StringUtils/stripToNull page)]
                                       (Integer/parseInt stripped-page))
                 keywords-vec (ensure-multiple-value-param keywords)

                 paginator (app/item-paginator page-owner-username clean-query keywords-vec)
                 items-page (paginator (or page-number 1))

                 item-keyword-count (app/count-item-keyword page-owner-username clean-query keywords-vec)]

                 (views/user-page is-viewing-owner page-owner-username
                                  clean-query keywords-vec
                                  item-keyword-count items-page)))))

(defn home-page [{session :session :as request}]
 (if-let [user (:identity session)]
         (response/redirect (str "/user/" user))
         (user-page nil nil nil nil request)))
