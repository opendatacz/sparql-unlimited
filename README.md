# sparql-unlimited

Executes SPARQL 1.1 Update operations split into pages.

## Usage

Enter configuration of your SPARQL endpoint into `config.yaml` or copy it, edit it, and provide it to the script via `--config` parameter.

```bash
java -jar sparql-unlimited.jar --help
```

## Development notes

* The stopping condition for paging is based on the count of bindings for variables projected by the SPARQL update's WHERE clause. The motivation for this solutions is discussed [here](http://answers.semanticweb.com/questions/29420/stopping-condition-for-paged-sparql-update-operations). Responses to no-op SPARQL updates aren't standardized:
  * For empty update, Virtuoso returns HTTP 200 response with "0 triples" in the body.
  * For empty update, Fuseki returns "standard" success HTTP 200 response without any indication how many triples were affected.

## Known caveats

* Virtuoso doesn't enable to use `COUNT(DISTINCT *)`, even though it's valid SPARQL.

## License

Copyright © 2014 Jindřich Mynarz

Distributed under the Eclipse Public License either version 1.0.
