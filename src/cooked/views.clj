(ns cooked.views
  (:require [clojure.pprint :as pprint]
            [clojure.string :as string]
            [config.core :refer [env]]
            [hiccup.core :refer [html]]
            [hiccup.form :refer [check-box]]
            [hiccup.page :refer [html5 include-css]]
            [ring.util.codec :as codec]))

(def default-meta [[:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]])

(defn- layout [maybe-context & contents]
  (if (map? maybe-context)
      (let [context maybe-context
            {:keys [title head]} context]

           (html5 {:lang "en"}
                  [:head [:title (or title "Cooked")]
                         (concat default-meta head [(include-css "/css/style.css")])]
                  (:google-analytics env)
                  [:body contents]))

      (apply layout (into [{} maybe-context] contents))))

(defn- item-view [{:keys [url title image-url video-url description keywords account]}]
 [:div {:class "view"}
  [:div {:class "logo"} [:a {:href "/"} [:img {:src "/cooked.png"}]]]
  [:div {:class "header"}
        [:div {:class "header-title"}
              [:pre title]
              [:a {:href url} url]]
        [:a {:href url} (if image-url
                            [:img {:class "item-img" :src image-url}]
                            [:div {:class "item-img empty-img"}])]]

  (when-not (empty? video-url) [:iframe {:src video-url :allowfullscreen true}])

  [:div {:class "body"}
        [:pre description]
        [:pre {:class "keywords"} (string/join ", " keywords)]]
   [:div {:class "author"} [:p "Created by " [:a {:href (str "/user/" account)} account]]
                           [:p "Is this yours? " [:a {:href "/user"} "Login"]]]])

(defn- item-edit [{:keys [url title image-url video-url description keywords]} is-new]
 [:div {:class "edit"}
  [:div {:class "logo"} [:a {:href "/"} [:img {:src "/cooked.png"}]]]
  [:div {:class "header"}
        [:div {:class "header-title"}
              [:textarea {:type "text" :name "title" :placeholder "Title" :required true} title]
              [:a {:href url} url]
              [:div {:class "options"}
                    (when-not is-new (list (check-box {:id "refresh-image"} "refresh-image" false)
                                           [:label {:for "refresh-image"} "Refresh image"]))]]
        [:a {:href url} (if image-url
                            [:img {:class "item-img" :src image-url}]
                            [:div {:class "item-img empty-img"}])]]

  (when-not (empty? video-url) [:iframe {:src video-url :allowfullscreen true}])

  [:div {:class "body"}
        [:textarea {:name "description" :placeholder "description"} description]
        [:textarea {:name "keywords" :placeholder "keyword," :spellcheck "false" :class "keywords"}
        (string/join ", " keywords)]]])

(defn new-item-page [{:keys [url image-url video-url] :as item} username]
 (layout
  [:form {:action "/new" :method "post" :class "item-page"}
         (item-edit item true)
         [:input {:type "hidden" :name "url" :value url}]
         [:input {:type "hidden" :name "image-url" :value image-url}]
         [:input {:type "hidden" :name "video-url" :value video-url}]
         (if (some? username)
             [:input {:type "submit" :name "save" :value "Save"}]
             [:a {:href "/user" :class "login-to-save"} "Login to save"])]))

(defn item-already-exists [{:keys [id url title image-url] :as item}]
 (layout
  [:div {:class "page already-exists"}
        [:div "Item for this url already exists."]
        [:a {:href (str "/saved/" id)} title]
        [:div {:class "item"}
              [:img {:src image-url}]]]))

(defn view-item-page [item-id {:keys [title description image-url] :as item}]
 (layout
  {:title title
   :head [[:meta {:property "og:image" :content image-url}]
          [:meta {:property "og:title" :content title}]
          [:meta {:property "og:description" :content description}]]}
  [:div {:class "item-page"}
        [:div {:class "item-update"}
              (item-view item)]]))

(defn edit-item-page [item-id item]
 (layout
  [:div {:class "item-page"}
        [:form {:action "" :method "post" :class "item-update"}
               (item-edit item false)
               [:input {:type "submit" :value "Update"}]]
        [:form {:action (str "/saved/deleted/" item-id)
                :onsubmit "return confirm('Are you sure?');"
                :method "post"
                :class "item-delete"}
               [:input {:type "submit" :value "Delete"}]]]))

(defn login [query-params]
 (layout
   [:div {:class "page user-page login"}
         [:form {:action "/login" :method "post"}
                [:input {:type "text" :name "username" :placeholder "Username"}]
                [:input {:type "password" :name "password" :placeholder "Password"}]
                [:input {:type "submit" :value "Login"}]]
         [:a {:href (str "/register?" (codec/form-encode query-params))} "register"]]))

(defn user [{:keys [username private]}]
 (layout
   [:div {:class "page user-page update"}
         [:form {:action "/user" :method "post"}
                [:input {:type "text" :value username :disabled true}]
                (check-box {:id "private-user"} "private" private)
                [:label {:for "private-user"} "Private"]
                [:input {:type "submit" :value "Update"}]
                [:a {:href "/saved.zip" :class "export"} "Export"]]
         [:form {:action "/logout" :method "post" :class "logout"}
                [:input {:type "submit" :value "Logout"}]]]))

(defn register []
 (layout
   [:div {:class "page user-page register"}
         [:form {:action "/register" :method "post"}
                [:input {:type "text" :name "username" :placeholder "Username"}]
                [:input {:type "password" :name "password" :placeholder "Password"}]
                [:input {:type "password" :name "password-confirm" :placeholder "Password"}]
                [:input {:type "submit" :value "Register"}]]]))

(defn- search-url [query keywords]
 (str "?" (codec/form-encode {:query query :keyword keywords})))

(defn- search-url-exclude-keyword [query keywords keyword]
 (search-url
   query
   (remove #{keyword} keywords)))

(defn- search-url-add-keyword [query keywords keyword]
 (search-url
   query
   (conj keywords keyword)))

(defn- user-pagination [{:keys [item page next-page previous-page]} query search-keywords]
 [:div {:class "pagination"}
       [:div {:class (if previous-page "previous" "previous disabled")}
             [:a {:href (str (search-url query search-keywords) "&page=" previous-page)} "previous"]]

       [:div {:class "current-page"} page]

       [:div {:class (if next-page "next" "next disabled")}
             [:a {:href (str (search-url query search-keywords) "&page=" next-page)} "next"]]])

(defn- user-keywords-panel [item-keyword-count query search-keywords]
 [:ul (for [{:keys [keyword keyword-count]} item-keyword-count]
                   [:li {:class "keyword"}
                        (if (some #{keyword} search-keywords)
                            [:span [:a {:href (search-url-exclude-keyword query search-keywords keyword)} "[x]"] keyword]
                            [:a {:href (search-url-add-keyword query search-keywords keyword)} keyword " (" keyword-count ")"])])])

(defn- user-search-panel [items query search-keywords user]
 (when-not (and (empty? items)
                (empty? query)
                (empty? search-keywords))
           [:form {:action "" :method "get"}
                  [:input {:type "text" :name "query" :value query}]

                  (for [keyword search-keywords]
                       [:input {:type "hidden" :name "keyword" :value keyword}])

                  [:input {:type "submit" :value "Search"}]

                  (when-not (and (empty? query) (empty? search-keywords))
                            [:a {:class "reset" :href (str "/user/" user)} "reset"])]))

(defn user-page [is-owner user query search-keywords
                 item-keyword-count {:keys [items] :as items-page}]

 (layout {:head [[:meta {:property "og:image" :content "https://cooked.wiki/cooked.png"}]
                 [:meta {:property "og:title" :content "Cooked"}]]}

         [:div {:class "search-pane"}
          [:div {:class "keywords"}
                [:div {:class "title"} [:a {:href "/"} [:img {:src "/cooked.png"}]]]
                (user-keywords-panel item-keyword-count query search-keywords)]

          [:div {:class "items-pane"}
                [:div {:class "search"}
                      [:div (user-search-panel items query search-keywords user)
                            (when (and is-owner (empty? query) (empty? search-keywords))
                                  [:p "Type cooked.wiki/ before any url to save it here."])
                            (when-not is-owner
                                      (list [:p "Created by the user &quot" user "&quot"]
                                            [:p "Is this you? " [:a {:href "/user"} "Login"]]))]

                      [:div {:class "account"} [:a {:href "/user"} "account"]]]

                [:ul {:class "items"}
                     (for [{:keys [id url title image-url]} items
                           :let [edit-item-url (str "/saved/" id)]]
                          [:li {:class "item"}
                               [:a {:href edit-item-url} [:img {:src image-url}]]
                               [:a {:href edit-item-url} title]])]

                (when (not-empty items)
                      (user-pagination items-page query search-keywords))]]))
