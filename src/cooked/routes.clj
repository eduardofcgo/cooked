(ns cooked.routes
 (:require [compojure.core :refer [defroutes GET POST]]
           [compojure.route :refer [not-found]]
           [cooked.controllers :as controllers]
           [cooked.views :as views]
           [ring.util.response :as response]))

(defroutes routes
  (GET "/" req (controllers/home-page req))

  (GET "/user/:user" [user query keyword page :as req]
                     (controllers/user-page user query keyword page req))

  (GET "/user" req (controllers/user req))
  (POST "/user" req (controllers/update-user req))

  (POST "/login" req (controllers/authenticate req))
  (GET "/register" req (controllers/user-register req))
  (POST "/register" req (controllers/register-user req))
  (POST "/logout" req (controllers/logout req))

  (GET "/new" [url :as req] (controllers/new-item-page url req))
  (POST "/new" req (controllers/save-item req))

  (GET "/saved/:id" [id :as req] (controllers/item-page id req))
  (POST "/saved/:id" [id :as req] (controllers/update-item id req))
  (POST "/saved/deleted/:id" [id :as req] (controllers/delete-item id req))

  (GET "/saved.zip" req (controllers/export req))

  (GET "/favicon.ico" req (response/redirect "/public/favicon.ico"))

  (GET "/*" req (controllers/new-item-shortcut req)))
