(defproject cooked "0.1.0-SNAPSHOT"
  :description "cooked"
  :url "https://github.com/eduardofcgo/cooked"
  :dependencies [
   [org.clojure/clojure "1.11.1"]
   [org.clojure/tools.logging "0.4.1"]
   [com.taoensso/timbre "6.0.4"]
   [com.fzakaria/slf4j-timbre "0.3.21"]
   [yogthos/config "1.2.0"]
   [com.taoensso/nippy "3.2.0"]
   [migratus/migratus "1.4.9"]
   [migratus-lein "0.7.3"]
   [org.clojure/java.jdbc "0.7.12"]
   [org.postgresql/postgresql "42.5.2"]
   [com.mchange/c3p0 "0.9.5.5"]
   [com.layerware/hugsql "0.5.3"]
   [hugsql-adapter-case "0.1.0"]
   [com.google.guava/guava "31.1-jre"]
   [org.apache.commons/commons-lang3 "3.12.0"]
   [commons-validator/commons-validator "1.7"]
   [org.jsoup/jsoup "1.15.3"]
   [org.clojure/data.json "2.4.0"]
   [ring "1.9.6"]
   [ring/ring-headers "0.3.0"]
   [compojure "1.6.2"]
   [buddy/buddy-auth "3.0.1"]
   [buddy/buddy-hashers "1.8.158"]
   [hiccup "1.0.5"]
   [clj-http "3.12.3"]
   [enlive "1.1.6"]
   [cheshire "5.11.0"]]
  :plugins [[lein-cljfmt "0.9.2"]
            [migratus-lein "0.7.3"]]
  :cljfmt {:indentation? false
           :sort-ns-references? true
           :remove-multiple-non-indenting-spaces? true}
  :main ^:skip-aot cooked.core
  :target-path "target/%s"
  :migratus {:store :database
             :migration-dir "migrations"
             :db {:connection-uri "jdbc:postgresql://0.0.0.0:5432/cooked?user=postgres&password=postgres"}}
  :profiles {:prod {:resource-paths ["config/prod"]}
             :dev  {:resource-paths ["config/dev"]}
             :uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
