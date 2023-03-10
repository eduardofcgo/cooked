(ns cooked.extract
 (:import (com.fasterxml.jackson.core JsonParseException)
          (com.google.common.net InternetDomainName)
          (de.l3s.boilerpipe.extractors DefaultExtractor ArticleExtractor)
          (java.io StringReader)
          (org.apache.commons.lang3 StringUtils)
          (org.jsoup Jsoup)
          (org.xml.sax InputSource))
 (:require
  [cheshire.core :as json]
  [clojure.pprint :as pprint]
  [clojure.string :as string]
  [net.cgrand.enlive-html :as html]))

(defn keywordize [s]
 (-> (StringUtils/stripAccents s)
     (string/replace #"[^\p{Alnum}\s\-]" "")
     (string/trim)
     (string/replace #"\s+" "-")
     (string/replace #"-+" "-")
     (string/lower-case)))

(defn valid-keyword? [keyword]
 (let [size (count keyword)]
      (and (> size 3)
           (< size 35))))

(defn- ensure-text [maybe-html-text]
 (when maybe-html-text (-> (Jsoup/parse maybe-html-text)
                           (.wholeText))))

(defn- collapse-whitespace [s]
 (StringUtils/stripToNull
  (string/replace s #"\s{3,}" "\n\n")))

(defn- json-parse-valid [s]
 (try (json/parse-string s)
      (catch JsonParseException e nil)))

(defn valid-microdatas [nodes]
 (filter some? (map json-parse-valid
                    (html/select
                      nodes
                      [[:script (html/attr= :type "application/ld+json")]
                       html/text-node]))))

(defn flatten-microdata [coll]
 (if-not (map? coll) (flatten coll)
                     coll))

(defn find-microdatas [nodes type]
 (->> (valid-microdatas nodes)
      (flatten-microdata)
      (flatten-microdata)
      (map #(or (get % "@graph") %))
      (flatten-microdata)
      (filter (fn [{found-type "@type"}]
                  (or (= found-type type)
                      (some #{type} found-type))))))

(defn videos-microdata [nodes]
 (find-microdatas nodes "VideoObject"))

(defn extract-meta [nodes attr value]
 (let [prop-selector [[:meta (html/attr= attr value)]]]
      (->> (html/select nodes prop-selector)
           (map #(get-in % [:attrs :content])))))

(defn extract-meta-og [nodes value]
 (or (first (extract-meta nodes :property value))
     (first (extract-meta nodes :name value))))

(defn split-double-newline [instructions]
 (mapcat #(string/split % #"\n\s*\n") instructions))

(defn trim-left-list-item [s]
 (string/replace-first s #"^\d?\s*(\;|\)|\-|\–|\•)\s*" ""))

(defn clean-recipe-ingredients [ingredients]
 (->> ingredients
      (map ensure-text)
      (map #(StringUtils/stripToNull %))
      (filter #(> (count %) 3))
      (map trim-left-list-item)
      (seq)))

(defn clean-recipe-instructions [instructions]
 (->> (split-double-newline instructions)
      (map ensure-text)
      (map #(StringUtils/stripToNull %))
      (filter #(> (count %) 7))
      (map trim-left-list-item)
      (seq)))

(defn- parse-recipe-instructions [instructions]
 (cond (string? instructions) [(ensure-text instructions)]
       (= (get instructions "@type") "HowToStep") [(or (get instructions "text")
                                                       (get instructions "name"))]
       (= (get instructions "@type") "HowToSection") (parse-recipe-instructions (get instructions "itemListElement"))
       (vector? instructions) (mapcat parse-recipe-instructions instructions)))

(defn- extract-str-recipe-prop [prop]
 (cond (string? prop) (StringUtils/stripToNull prop)
       (sequential? prop) (extract-str-recipe-prop (first prop))))

(defn- ensure-vector-recipe-prop [prop]
 (cond (sequential? prop) prop
       (string? prop) [prop]))

(defn- extract-keywords-recipe-prop [prop]
 (cond (string? prop) (string/split prop #",")
       (sequential? prop) (flatten (map extract-keywords-recipe-prop prop))))

(defn extract-recipe-microdata [nodes]
 (let [found-recipe (first (find-microdatas nodes "Recipe"))
       {name "name"
        description "description"
        image-url "image"
        yield "recipeYield"
        categories "recipeCategory"
        cuisines "recipeCuisine"
        keywords "keywords"
        section "articleSection"
        ingredients "recipeIngredient"
        instructions "recipeInstructions"} found-recipe]
      (when found-recipe {:title (ensure-text (extract-str-recipe-prop name))
                         :description (ensure-text (extract-str-recipe-prop description))
                         :image-url (extract-str-recipe-prop image-url)
                         :yield (ensure-text (extract-str-recipe-prop yield))
                         :ingredients (clean-recipe-ingredients (ensure-vector-recipe-prop ingredients))
                         :instructions (clean-recipe-instructions (parse-recipe-instructions instructions))
                         :keywords (->> (extract-keywords-recipe-prop keywords)
                                        (concat (ensure-vector-recipe-prop categories))
                                        (concat (ensure-vector-recipe-prop cuisines))
                                        (concat (ensure-vector-recipe-prop section))
                                        (map keywordize)
                                        (distinct))})))

(defn extract-recipe-rdf [jsoup-document]
 (let [recipe-element (-> jsoup-document (.select "[itemtype$=schema.org/Recipe]") (.first))
       found-recipe (some? recipe-element)

       extract-texts (fn [query]
                         (->> (.select recipe-element query)
                              (map #(.wholeText %))
                              (map #(StringUtils/stripToNull %))
                              (filter #(> (count %) 3))
                              (seq)))

       extract-text-combine (fn [query]
                                (StringUtils/stripToNull
                                  (.text (.select recipe-element query))))]

      (when found-recipe {:yield (StringUtils/stripToNull (extract-text-combine "[itemprop='recipeYield']"))
                          :ingredients (clean-recipe-ingredients (or (extract-texts "[itemprop='recipeIngredient']")
                                                                     (extract-texts "[itemprop='ingredients']")))
                          :instructions (clean-recipe-instructions (extract-texts "[itemprop='recipeInstructions']"))
                          :keywords (->> (extract-texts "[itemprop='keywords']")
                                         (extract-keywords-recipe-prop)
                                         (concat (extract-texts "[itemprop='recipeCategory']"))
                                         (concat (extract-texts "[itemprop='recipeCuisine']"))
                                         (concat (extract-texts "[itemprop='articleSection']"))
                                         (map keywordize)
                                         (distinct))})))

(defn extract-text-listing [element]
 (when element
       (->> (.select element "*")
            (map #(.wholeOwnText %))
            (split-double-newline)
            (map #(StringUtils/stripToNull %))
            (filter #(> (count %) 3))
            (map trim-left-list-item)
            (seq))))

(defn- extract-structured-recipe-html [jsoup-document]
 (let [ingredients-match (first (.select jsoup-document "[class~=ingredients]"))
       instructions-match (first (.select jsoup-document "[class~=instructions|preparation|directions]"))

       ingredients (extract-text-listing ingredients-match)
       instructions (extract-text-listing instructions-match)]
      (when (or ingredients instructions)
            {:ingredients ingredients
             :instructions instructions})))

(defn- extract-whole-recipe-html [jsoup-document]
 (let [selectors [".recipe-callout"
                  ".tasty-recipes"
                  ".easyrecipe"
                  ".innerrecipe"
                  ".recipe-summary.wide"
                  ".wprm-recipe-container"
                  ".recipe-content"
                  ".simple-recipe-pro"
                  ".mv-recipe-card"
                  "#recipecard"
                  "#recipecardo"]

       group-selector (string/join ", " selectors)
       match (first (.select jsoup-document group-selector))]

      (when match {:description (collapse-whitespace (.wholeText match))})))

(defn extract-recipe-html [jsoup-document]
 (let [{:keys [ingredients instructions] :as recipe} (extract-structured-recipe-html jsoup-document)
       is-recipe-complete (and ingredients instructions)
       is-likely-clean-instructions (< (count instructions) 10)]

       (if (and is-recipe-complete is-likely-clean-instructions)
           recipe
           (or (extract-whole-recipe-html jsoup-document) recipe))))

(defn- extract-html [html-text extractor]
 (let [source (doto (new InputSource (new StringReader html-text))
                    (.setEncoding "UTF-8"))]
      (.getText extractor source)))

(defn extract-article-html [html-text]
 (let [full-article (extract-html html-text DefaultExtractor/INSTANCE)
       news-article (extract-html html-text ArticleExtractor/INSTANCE)

       before-news (StringUtils/substringBefore full-article news-article)
       news (StringUtils/substringAfter news-article before-news)

       found-before-news (not= full-article before-news)]

       (cond (not found-before-news)
                news-article
             (and found-before-news (not-empty news))
               (str before-news news)
             (and found-before-news (empty? news))
               (str before-news news-article))))

(defn build-recipe-text [{:keys [description yield ingredients instructions]}]
  (when (or description ingredients instructions)
        (str (when (some? description) (ensure-text description))
             (when (some? yield) (str "\n" "Yields: " (ensure-text yield)))

             (when (some? ingredients)
                   (str "\n\n" (string/join "\n" (map #(str "- " (ensure-text %)) ingredients))))

             (when (some? instructions)
                   (str "\n\n" (string/join "\n" (map #(str %1 ". " (ensure-text %2)) (range 1 ##Inf) instructions))))

             "\n")))

(defn extract-title [nodes]
 (or (extract-meta-og nodes "og:title")
     (first (html/select nodes [:title html/text-node]))))

(defn extract-description [nodes]
 (or (extract-meta-og nodes "og:description")
     (extract-meta-og nodes "description")))

(defn extract-image-url [nodes]
 (extract-meta-og nodes "og:image"))

(defn extract-video-url [nodes]
 (or (extract-meta-og nodes "og:video:url")
     (let [video (first (videos-microdata nodes))]
          (or (get video "embedUrl")
              (get video "contentUrl")))))

(defn extract-keywords [nodes]
 (->> (extract-meta nodes :name "keywords")
      (mapcat #(string/split % #","))
      (map keywordize)
      (distinct)))

(defn- get-website-name [url]
  (let [host (-> (java.net.URI/create url) .getHost)
        domain (-> (InternetDomainName/from host) .topPrivateDomain)
        domain-name (.toString domain)
        suffix (str "." (-> domain .publicSuffix .toString))]
       (->> domain-name (drop-last (count suffix)) string/join)))

(defn url-keyword [url]
 (-> url
     get-website-name
     (string/replace #"\." "-")
     keywordize))

(defn extract-from-url [url]
 {:url url
  :keywords [(url-keyword url)]})

(defn extract-from-page [page-text url]
 (let [nodes (html/html-resource (java.io.StringReader. page-text))
       jsoup-document (Jsoup/parse page-text)

       {recipe-title :title
        recipe-description :description
        recipe-keywords :keywords
        recipe-image-url :image-url :as recipe} (or (extract-recipe-microdata nodes)
                                                    (extract-recipe-rdf jsoup-document)
                                                    (extract-recipe-html jsoup-document))]
      {:url url
       :title (ensure-text (or recipe-title (extract-title nodes)))
       :image-url (or recipe-image-url (extract-image-url nodes))
       :video-url (extract-video-url nodes)

       :recipe recipe

       :description (or (build-recipe-text recipe) (extract-article-html page-text))

       :keywords (filter valid-keyword? (-> (extract-keywords nodes)
                                            (concat recipe-keywords)
                                            (conj (url-keyword url))
                                            (conj (when (some? recipe) "recipe"))
                                            (distinct)
                                            (sort)))}))
