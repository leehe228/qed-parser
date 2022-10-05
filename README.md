# Cosette Parser

SQL parser for the [Cosette Solver](https://github.com/cosette-solver)
based on that of [Apache Calcite](https://calcite.apache.org/).

## Requirements

Java version 17.

## Get and Run the Cosette parser

### Download and build

```bash
$ git clone git://github.com/cosette-solver/cosette-parser.git
$ cd cosette-parser
$ ./mvnw install
```

### Run

One can start the main class "org.cosette.Main" on JVM manually, with a path containing SQL files as the command line arguments.
Or you may use
```bash
$ ./mvnw exec:exec -Dargs="<input-paths>"
```

## License

Copyright 2021 The Cosette Team

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with
the License. You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "
AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.
