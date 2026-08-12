# Verification Notes

## Completed in the generation environment

- Java 21 is installed.
- All main Java sources were compiled with `javac --release 21` against minimal local stubs for the external Spring/Jackson APIs. This validates Java syntax, types between project classes, records, sealed types, switches, and method signatures used inside the project.
- All Java test sources were also compiled with `javac --release 21` against minimal local stubs for JUnit/Spring Test/Jackson APIs.
- 59 JUnit `@Test` methods are present.
- All example/request JSON files pass `python3 -m json.tool` validation.
- All Bash scripts pass `bash -n`.
- `scripts/print-test-results.py` passes Python bytecode compilation.

## Environment limitation

The generation container does not have Maven or Gradle installed, and outbound DNS is unavailable. Therefore it could not download Maven/Spring/JUnit dependencies and could not execute the real Maven/JUnit suite here.

On a normal networked Linux development machine, run:

```bash
./scripts/test-all.sh
```

The included `mvnw` script uses an existing `mvn` if present; otherwise it bootstraps Apache Maven 3.9.16 and then runs the project normally.
