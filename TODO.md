# TODO — Modernize `moleculer-java-jmx` to 2.0.0

> **You are the per-project Claude Code instance for `moleculer-java-jmx`.** Self-contained file.
> Goal: Gradle/Java 8 → **Maven + JDK 21**, deps upgraded, tests green on **JUnit 5**, legacy files
> removed, version **2.0.0**. Small module: one Moleculer service (`JmxService`) that exposes JVM
> JMX MBeans as Moleculer actions/events. Packages: `services.moleculer.jmx`.

## Coordinates & facts
- Maven: `com.github.berkesa:moleculer-java-jmx`, `jar`, license **MIT**.
- `name`: *Moleculer JMX Service* · `inceptionYear`: 2019
- `url`: https://moleculer-java.github.io/moleculer-java-jmx/ · `scm`: https://github.com/moleculer-java/moleculer-java-jmx.git
- developer: `berkesa` / Andras Berkes / andras.berkes@programmer.net
- **Version → `2.0.0`** (old build hard-codes it in 3 places: `version`, `jar`, and the
  `moleculer-java` dependency — in Maven that collapses to `<version>` + the dependency `<version>`).

## Inter-project dependency (PIN to 2.0.0)
- `com.github.berkesa:moleculer-java:2.0.0` (was **1.2.6** impl / **1.2.4** POM — far behind core's
  1.2.28; now unified). Build `moleculer-java` (and its datatree deps) first.

## Target versions
| Dependency | Current | Target | Scope |
|---|---|---|---|
| `com.github.berkesa:moleculer-java` | 1.2.6 / 1.2.4 | **2.0.0** | compile |
| `org.slf4j:slf4j-api`, `slf4j-jdk14`, `log4j-over-slf4j`, `jcl-over-slf4j` | 1.7.30 | **2.0.18** | runtime/compile |
| `junit:junit` 4.12 | → `junit-jupiter` | **5.x** | test |
| Eclipse `ecj` 4.4.2 | — | **remove** | — |
| Java | 1.8 | **21** | — |

## Steps
1. **`pom.xml`** (metadata + MIT + `release=21`). Deps: `moleculer-java:2.0.0-SNAPSHOT`, slf4j 2.0.18
   (the four jars), `junit-jupiter` test. Build plugins: compiler **3.15.0**, surefire **3.5.4** (lockstep), (release
   profile) sources/javadoc/gpg + central-publishing 0.9.0. The old `javadoc { failOnError = true }`
   → either fix javadoc or `<doclint>none</doclint>`.
2. **Remove ECJ** → javac.
3. **Tests → JUnit 5.** `JmxServiceTest` is JUnit 3 `TestCase` style; convert to Jupiter. It spins
   up an **in-process RMI registry on port 1234** plus the local platform MBean server — it's
   self-contained, so keep it running (ensure port 1234 is free; otherwise tag/disable). `JmxListener`
   is a test-only Moleculer `Service`. `mvn test` green.
4. **Preserve API contracts:** keep the stable error codes (`NO_JMX`, `NO_CTX`, `NO_OBJECT_NAME`,
   `NO_ATTRIBUTE_NAME`, `NO_QUERY`), the four actions (`jmx.listObjectNames`/`getObject`/
   `getAttribute`/`findObjects`), the NaN/Infinity→null JSON coercion, the watcher
   add/remove-only-while-stopped rule, and the `protected` visibility convention.
5. **Cleanup — delete:** `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `gradle/`,
   `.gradle/`, `.travis.yml`, `.codacy.yaml`, `.classpath`, `.project`, `.settings/`.
6. **VSCode + .gitignore** (library — no `launch.json`).
7. **Build & install:** `mvn clean install`, then `mvn clean verify`.
8. **Update `CLAUDE.md`:** Maven commands; version now in 2 Maven spots (pom `<version>` + the
   `moleculer-java` dependency `<version>`), both `2.0.0`.

## Definition of done
- `mvn clean verify` green on JDK 21; JUnit 5; legacy files gone; VSCode + .gitignore.
- Depends on `moleculer-java:2.0.0`; version `2.0.0`; publishing configured.
