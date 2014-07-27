(defproject sparql-unlimited "0.1.0-SNAPSHOT"
  :description "Executing SPARQL updates using paging."
  :url "http://github.com/opendatacz/sparql-unlimited"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.taoensso/timbre "3.1.1"]
                 [clj-yaml "0.4.0"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 [org.clojure/tools.cli "0.3.1"]
                 [clj-http "0.6.3"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.apache.jena/jena-core "2.11.1"]
                 [org.apache.jena/jena-arq "2.11.2"]
                 [intervox/clj-progress "0.1.1"]]
  :main sparql-unlimited.core
  :profiles {:uberjar {:aot :all}})
