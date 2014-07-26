PREFIX sd: <http://www.w3.org/ns/sparql-service-description#>

INSERT {
    ?graph sd:endpoint "bork" .
}
USING NAMED <http://example.com/graph1>
USING NAMED <http://example.com/graph2>
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
