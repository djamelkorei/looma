# Looma for IntelliJ

IDE support for [Looma](https://djamelkorei.github.io/looma/) templates (`*.looma.html`): highlighting on
top of HTML, the engine's errors as you type, navigation from templates to templates, components, message
keys and the Java code that feeds them (controllers, model attributes, getters, helpers, beans), and
completion for all of it.

See the [plugin page](https://djamelkorei.github.io/looma/intellij/) for the full list.

```bash
./gradlew buildPlugin    # build/distributions/*.zip -> Settings | Plugins | Install Plugin from Disk
./gradlew runIde         # try it in a sandbox IDE
./gradlew test           # run `mvn -pl looma-core compile` first for the engine sync tests
```

Supports IntelliJ IDEA 2025.2 and newer. Java features need the Java plugin; `@lang` keys need Properties.
