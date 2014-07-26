INSERT {
  GRAPH <http://example.com/graph> {
    ?s ?p ?o .
    ?o ?p ?s .
  }
}
WHERE {
  GRAPH <http://example.com/graph> {
    ?s ?p ?o .
  }
}
