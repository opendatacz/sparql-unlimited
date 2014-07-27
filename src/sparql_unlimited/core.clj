(ns sparql-unlimited.core
  (:gen-class)
  (:require [taoensso.timbre :as timbre]
            [clj-yaml.core :as yaml]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :as io]
            [clj-http.client :as client]
            [clojure.xml :as xml]
            [clojure.data.zip.xml :as zip-xml]
            [clojure.zip :as zip]
            [clj-progress.core :as progress])
  (:import [com.hp.hpl.jena.update Update UpdateFactory]
           [com.hp.hpl.jena.query Query]
           [com.hp.hpl.jena.sparql.core Var]
           [com.hp.hpl.jena.sparql.syntax ElementGroup ElementSubQuery PatternVars]
           [com.hp.hpl.jena.sparql.expr ExprAggregator]
           [com.hp.hpl.jena.sparql.expr.aggregate AggCount]))

(declare file-exists? get-quad-variables sparql-query)

; ----- Private vars -----

(def ^:private
  cli-options
  [["-c" "--config CONFIG" "Path to config YAML file"
    :default "config.yaml"
    :validate [#(file-exists? %) "SPARQL endpoint configuration doesn't exist."]]
   ["-h" "--help"]
   [nil "--log" "Use to turn logging SPARQL queries on"]
   ["-p" "--page PAGE" "Size of page processed in a single update operation"
    :default 1000
    :parse-fn #(Integer/parseInt %)]
   ["-u" "--update UPDATE" "Path to SPARQL Update file"
    :validate [#(file-exists? %) "You need to provide a path to existing file."]]])

; ----- Private functions -----

(defn- exit
  "Exit using @status with @message.
  0 is OK status, 1 indicates an error."
  [status message]
  {:pre [(contains? #{0 1} status)]}
  (println message)
  (System/exit status))

(defn file-exists?
  "Test if @file-path is an existing file."
  [file-path]
  (.exists (io/as-file file-path)))

(defn- init-logger
  "Initialize logger"
  [enabled?]
  (timbre/set-level! :info)
  (timbre/set-config! [:appenders :standard-out :enabled?] enabled?))

(defn- init-progress-bar
  []
  (progress/set-progress-bar! ":header [:bar] :percent :done/:total updates done"))

(defn- load-config
  "Load SPARQL endpoint configuration.
  Returns false if @config-path doesn't exist."
  [config-path]
  (if (.exists (io/as-file config-path))
    (yaml/parse-string (slurp config-path))
    false))

(defn- usage
  [options-summary]
  (->> ["SPARQL Update unlimited executes SPARQL 1.1 update operations split into pages."
        ""
        "Usage: java -jar sparql-unlimited.jar [options]"
        ""
        "Options:"
        options-summary
        ""]
       (clojure.string/join \newline)))

(defn- xml->zipper
  "Take XML string @s, parse it, and return XML zipper"
  [s]
  (->> s
       .getBytes
       java.io.ByteArrayInputStream.
       xml/parse
       zip/xml-zip))

; ----- Public functions -----

(defn get-count-query
  "Generate a SPARQL SELECT query that COUNTs all bindings for named variables
  in @update operation."
  [update]
  (str (doto (Query.)
         (.setQuerySelectType)
         (.setQueryPattern (:where-pattern update))
         (.addResultVar "count" (ExprAggregator. (Var/alloc "count") (AggCount.))))))

(defn get-paged-query
  "Return @sort-query wrapped in a query with paging using @limit and @offset."
  [^ElementSubQuery sort-query
   ^java.util.LinkedHashSet variables
   & {:keys [limit offset]}]
  {:pre [(instance? ElementSubQuery sort-query)
         (seq? variables)]}
  (doto (ElementGroup.)
    (.addElement (ElementSubQuery. (doto (Query.)
                                     (.setQuerySelectType)
                                     (.addProjectVars variables)
                                     (.setQueryPattern sort-query)
                                     (.setLimit limit)
                                     (.setOffset offset))))))

(defn get-projected-variables
  "Extract projected variables (either INSERT or DELETE clause)
  from SPARQL Update operation @update."
  [^Update parsed-update]
  {:pre [(instance? Update parsed-update)]}
  (let [quads (concat (when (.hasInsertClause parsed-update) (.getInsertQuads parsed-update))
                      (when (.hasDeleteClause parsed-update) (.getDeleteQuads parsed-update)))]
    (mapcat get-quad-variables quads)))

(defn get-quad-variables
  "Extract variables from a @quad."
  [quad]
  (filter (partial instance? Var)
          (list (.getGraph quad)
                (.getSubject quad)
                (.getPredicate quad)
                (.getObject quad))))

(defn get-sort-query
  "Construct a sorted sub-query for @where-pattern projecting @variables.
  Sorts in ascending order by the first variable in @variables."
  [{:keys [variables where-pattern]}]
  (ElementSubQuery. (doto (Query.)
                      (.setQuerySelectType)
                      (.addProjectVars variables)
                      (.setQueryPattern where-pattern)
                      (.addOrderBy (first variables) 1)))) ; 1 is ASC

(defn parse-update
  "Parse a SPARQL 1.1 Update operation from @file-path."
  [file-path]
  (let [parsed-update (-> file-path
                          io/input-stream
                          UpdateFactory/read
                          first)] ; Only supports a single update operation 
    {:update parsed-update
     :variables (get-projected-variables parsed-update) 
     :where-pattern (.getWherePattern parsed-update)}))

(defn select-query
  "Execute SPARQL SELECT query @query-string using @sparql-endpoint."
  [sparql-endpoint query-string]
  (let [results (xml->zipper (sparql-query sparql-endpoint
                                           query-string
                                           :accept "application/sparql-results+xml"))
        sparql-variables (map keyword (zip-xml/xml-> results :head :variable (zip-xml/attr :name)))
        sparql-results (zip-xml/xml-> results :results :result)
        get-bindings (comp (partial zipmap sparql-variables) #(zip-xml/xml-> % :binding zip-xml/text))]
    (map get-bindings sparql-results)))

(defn sparql-query
  "Execute SPARQL query @query-string using @sparql-endpoint."
  [sparql-endpoint query-string & {:keys [accept]}]
  (let [query-url (:query-url sparql-endpoint)
        params {:accept accept 
                :query-params {:query query-string}}]
    (timbre/debug (str "Sending SPARQL query:" \newline query-string))
    (try
      (:body (client/get query-url params))
      (catch java.net.UnknownHostException e
        (throw (java.net.UnknownHostException.
                 (format "SPARQL endpoint %s doesn't exist." query-url)))))))

(defn sparql-update
  "Execute SPARQL 1.1 @update-string using @sparql-endpoint."
  [sparql-endpoint update-string]
  (let [{:keys [update-url username password]} sparql-endpoint
        params {:digest-auth [username password]
                :form-params {:update update-string}}]
    (timbre/info (str "Sending SPARQL update:" \newline update-string))
    (try
      (:body (client/post update-url params))
      (catch java.net.UnknownHostException e
        (throw (java.net.UnknownHostException.
                 (format "SPARQL endpoint %s doesn't exist." update-url)))))))

(defn sparql-update-unlimited
  "Execute SPARQL Update operation from @update-file-path using @sparql-endpoint
  by splitting the operation in pages of size @page-size."
  [sparql-endpoint update-file-path & {:keys [page-size]
                                       :or {page-size 1000}}]
  (let [update (parse-update update-file-path)
        results-count (->> (get-count-query update)
                           (select-query sparql-endpoint)
                           first
                           :count
                           Integer/parseInt)
        sort-update (assoc update :sort-query (get-sort-query update))
        render-query-fn (fn [offset] (str (doto (:update update)
                                       (.setElement (get-paged-query (:sort-query sort-update)
                                                                     (:variables sort-update)
                                                                     :limit page-size
                                                                     :offset offset)))))
        paged-query-fn (fn [offset] (sparql-update sparql-endpoint (render-query-fn offset)))]
    (progress/init "Executing paged SPARQL Update"
                   (int (java.lang.Math/ceil (/ results-count page-size))))
    (doseq [offset (iterate (partial + page-size) 0)
            :while (> results-count offset)]
      (do (paged-query-fn offset)
          (progress/tick)))
    (progress/done)))

(defn -main
  [& args]
  (let [{{:keys [config help log page update]} :options
         :keys [errors summary]} (parse-opts args cli-options)]
    (cond help (exit 0 (usage summary))
          errors (exit 1 (clojure.string/join \newline errors))
          :else (let [sparql-endpoint (load-config config)]
                  (do (init-logger (not (nil? log)))
                      (init-progress-bar)
                      (try
                        (sparql-update-unlimited sparql-endpoint
                                                 update
                                                 :page-size page)
                        (catch java.net.UnknownHostException e
                          (exit 1 (.getMessage e)))))))))
