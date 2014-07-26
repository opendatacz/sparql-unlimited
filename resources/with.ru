PREFIX sd: <http://www.w3.org/ns/sparql-service-description#>

WITH <http://example.com/graph>
INSERT {
    ?graph sd:endpoint "bork" .
}
WHERE {
  {
    SELECT ?graph
      WHERE {
        ?graph a sd:Graph .
      }
    OFFSET 2
    LIMIT 1
  } 
}
