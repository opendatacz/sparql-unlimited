# sparql-unlimited

Executes SPARQL 1.1 Update operations split into pages.

## Usage

Enter configuration of your SPARQL endpoint into `config.yaml` or copy it, edit it, and provide it to the script via `--config` parameter.

```bash
java -jar sparql-unlimited.jar --help
```

## Known caveats

* Virtuoso doesn't enable to use `COUNT(DISTINCT *)`, even though it's valid SPARQL.

## License

Copyright © 2014 Jindřich Mynarz

Distributed under the Eclipse Public License either version 1.0.
