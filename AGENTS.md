# AGENTS.md

This file provides guidance to agents working with code in this repository. It only lists what is **not** already covered
in [DEVELOPING.md](DEVELOPING.md); read that first for prerequisites, build/test/integration-test commands, the
`@ConfigValue` configuration pattern, exception handling, style/copyright, code coverage, POM conventions and the
release process. See [README.md](README.md) for a project introduction.

## Project Overview

Java 8 bytecode (compiled with Java 11+), Maven multi-module project.

### Modules

- `annotation`: custom annotations used for documentation (e.g. `@ConfigValue`) and the annotation processor.
- `common-lite`: lightweight subset of `common`, designed for sharing with other projects. It was split out of `common` because `common` pulls in many more transitive dependencies.
- `common`: general common functionality. Other projects may import it (e.g. [Gradle Manipulator](https://github.com/project-ncl/gradle-manipulator)).
- `io`: similar to the common modules but focused on I/O: JSON, files, POMs, XML and REST.
- `core`: where everything happens; all manipulators etc. live here.
- `cli`: the command line tool. Small; hooks into `core`.
- `ext`: builds PME as a Maven extension. Rarely used but the functionality is retained. Like the CLI it is small and just hooks into the Maven lifecycle.
- `integration-test`: integration tests. These are slow (roughly 15-30 minutes) and only run with `-Prun-its`.
- `coverage-reporting`: small module that aggregates the coverage reports.

## Additional Commands

```bash
# Run a single unit test class (from repo root or module directory)
mvn -pl core test -Dtest=VersioningCalculatorTest

# Run a single unit test method
mvn -pl core test -Dtest=VersioningCalculatorTest#applyNonSerialSuffix_NonNumericVersionTail_WithProperty

# Format code
mvn spotless:apply
```

## Code Style Details

- Spotless runs at the `compile` phase, so `mvn compile`/`mvn test` will reformat code. Run `mvn spotless:apply` before committing to avoid unexpected diffs. POM files are also sorted by Spotless.
- Import order (blank-line separated groups): `java`, `javax`, `jakarta`, `org`, `com`; static imports first.
- Use **Lombok** (`@Getter`, `@Setter`, etc.) rather than hand-written accessors.
- DI uses `javax.inject` (`@Inject`, `@Named`, `@Singleton`) via Sisu/Plexus, not Spring or CDI.
- `animal-sniffer` enforces Java 8 API compatibility on production classes (test classes excluded); newer APIs fail the build at `process-classes`.
- Omitting `@ConfigValue` on a configuration property silently bypasses CLI/EXT input validation (see DEVELOPING.md).

## Testing Patterns

- Tests use **JUnit 4** (not JUnit 5) + Hamcrest. Use `@Rule public TemporaryFolder temp` for file operations.
- Tests requiring Plexus DI use `@RunWith(PlexusTestRunner.class)` from `core/src/test/java/.../fixture/`.
- `TestUtils` in `core/src/test/java/.../fixture/TestUtils.java` bootstraps sessions.
- Test output is redirected to file (`redirectTestOutputToFile=true`); check `target/surefire-reports/` on failure.
- Test Groovy scripts live in `core/src/test/resources/*.groovy` and are referenced by path.

## Architecture Notes

- All manipulators implement the `Manipulator` interface and are ordered by `getExecutionIndex()` on a 100-point scale.
- Each `Manipulator` stores its config/state in a `State` subclass retrieved from `ManipulationSession`.
- Groovy manipulators (`InitialGroovyManipulator`, `FinalGroovyManipulator`, `PreparseGroovyManipulator`) run at fixed positions relative to other manipulators.
- Javadoc is generated from delomboked sources in `target/javadoc-generated-sources`, not `src/main/java`.
