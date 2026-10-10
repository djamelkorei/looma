---
title: Installation
---

# Installation

Looma needs **Java 21+** and **Spring Boot 3.3 or newer** (see [compatibility](configuration.md#compatibility)).

=== "Maven"

    ```xml
    <dependency>
      <groupId>io.github.djamelkorei</groupId>
      <artifactId>looma-spring-boot-starter</artifactId>
      <version>0.1.0</version>
    </dependency>
    ```

=== "Gradle (Kotlin)"

    ```kotlin
    implementation("io.github.djamelkorei:looma-spring-boot-starter:0.1.0")
    ```

=== "Gradle (Groovy)"

    ```groovy
    implementation 'io.github.djamelkorei:looma-spring-boot-starter:0.1.0'
    ```

That is the whole setup. Spring Boot auto-configures the engine and the view resolver; put templates in
`src/main/resources/templates/` as `*.looma.html` and continue with [Getting started](getting-started.md).

Only need the engine without Spring? Use `looma-core` (it depends on `spring-expression` only).

## From GitHub Packages (alternative)

Every release is also published to GitHub Packages. It works the same, but GitHub requires a token even to
download, so prefer Maven Central unless you have a reason.

Create a [personal access token](https://github.com/settings/tokens) with `read:packages` and add it to
`~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_TOKEN</password>
    </server>
  </servers>
</settings>
```

Then add the repository next to the dependency above:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/djamelkorei/looma</url>
  </repository>
</repositories>
```
