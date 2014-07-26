(ns sparql-unlimited.core
  (:require [clojure.java.io :as io])
  (:import [com.hp.hpl.jena.update UpdateFactory]
           [com.hp.hpl.jena.sparql.core Quad]
           [com.hp.hpl.jena.query Query]
           [com.hp.hpl.jena.sparql.syntax ElementGroup ElementNamedGraph ElementSubQuery
                                          ElementTriplesBlock PatternVars]))

(defn- quads->string
  "Translated from <http://svn.apache.org/repos/asf/jena/trunk/jena-arq/src/main/java/com/hp/hpl/jena/sparql/modify/UpdateEngineWorker.java>"
  [quad-list]
  (let [el (ElementGroup.)
        x (ElementTriplesBlock.)
        default-graph (Quad/defaultGraphNodeGenerated)]
    (doall (for [quad (vec quad-list)
                 :let [graph (.getGraph quad)
                       x (ElementTriplesBlock.)]]
             (do (when-not (= graph default-graph)
                   (do 
                       (if (nil? graph)
                         (.addElement el x)
                         (.addElement el (ElementNamedGraph. graph x)))))
                 (.addTriple x (.asTriple quad))
                 (.addElement el x))))
    (str el)))

(defn parse-update
  "Parse SPARQL 1.1 Update operation from @file-path."
  [file-path]
  {:pre [(.exists (io/as-file file-path))]}
  (-> file-path
      io/input-stream
      UpdateFactory/read
      first)) ; Update operation can contain multiple actions.

(comment
  (def update (parse-update "resources/simple.ru"))

  (def with-iri (if-let [iri (.getWithIRI update)]
                  (str iri)))
  (def using-iris (if-let [iris (seq (.getUsing update))]
                    (map str iris)))
  (def using-named-iris (if-let [iris (seq (.getUsingNamed update))]
                          (map str iris)))
  
  (cond (.hasInsertClause update)
        {:type "INSERT" :quads (.getInsertQuads update)}
        (.hasDeleteClause update)
        {:type "DELETE" :quads (.getDeleteQuads update)})
  (def quads (.getInsertQuads update))

  (println (quads->string quads))

  (def where-pattern (.getWherePattern update))
  (println (str where-pattern))
  (def variables (PatternVars/vars where-pattern))

  (defn get-sort-query
    [query-pattern variables]
    (ElementSubQuery. (doto (Query.)
                        (.setQuerySelectType)
                        (.addProjectVars variables)
                        (.setQueryPattern query-pattern)
                        (.addOrderBy (first variables) 1)))) ; 1 is ASC

  (def sort-query (get-sort-query where-pattern variables))
  (println (str sort-query))

  (defn get-paged-query
    [sort-query variables & {:keys [limit offset]}]
    (doto (ElementGroup.)
      (.addElement (ElementSubQuery. (doto (Query.)
                                       (.setQuerySelectType)
                                       (.addProjectVars variables)
                                       (.setQueryPattern inner-query)
                                       (.setLimit limit)
                                       (.setOffset offset))))))

  (def sub-query (get-paged-query sort-query
                                  variables
                                  :limit 500 :offset 0)))

  (.setElement update sub-query)
  (println (str update))

  )
