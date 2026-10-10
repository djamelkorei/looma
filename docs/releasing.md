---
title: Releasing
---

# Releasing (maintainers)

Releases go to **Maven Central** (so users add one dependency, no token) and also to GitHub Packages.
Everything is automated by `.github/workflows/release.yml` once the one-time setup below is done.

## One-time setup

You need four things that only the project owner can create. Do these once.

### 1. Central Portal account and namespace

1. Sign in at <https://central.sonatype.com> **with GitHub**.
2. Open **Namespaces**. `io.github.djamelkorei` is the namespace this project uses. Signing in with GitHub
   normally registers it for you; if it shows as *unverified*, follow the portal's instructions (it asks you to
   create a temporary public repository whose name is a key it shows you, then press *Verify*).

### 2. A user token

In the portal: your name (top right) → **View Account** → **Generate User Token**. Keep the username and
password it shows; they are *not* your login.

### 3. A signing key

Central requires every file to be signed with a PGP key whose **public** half is on a public keyserver.

```bash
brew install gnupg                       # macOS; any gpg 2.x works
gpg --full-generate-key                  # RSA 4096, expiry 2y is fine, use your real name and a passphrase
gpg --list-secret-keys --keyid-format long   # note the key id after "rsa4096/"
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
gpg --armor --export-secret-keys <KEY_ID> > looma-signing.key   # keep this file private, never commit it
```

Also upload the public key at <https://keys.openpgp.org> (it emails you to confirm) so more verifiers find it.

### 4. Repository secrets

```bash
gh secret set CENTRAL_USERNAME   -R djamelkorei/looma     # from step 2
gh secret set CENTRAL_PASSWORD   -R djamelkorei/looma     # from step 2
gh secret set MAVEN_GPG_KEY      -R djamelkorei/looma < looma-signing.key
gh secret set MAVEN_GPG_PASSPHRASE -R djamelkorei/looma   # the passphrase from step 3
rm looma-signing.key                                      # or store it in a password manager
```

Signing runs inside Maven (BouncyCastle), so the CI runner needs no gpg install.

## Cutting a release

```bash
gh release create v0.1.0 --generate-notes
```

The workflow takes `0.1.0` from the tag, runs the full test suite, builds sources and javadoc jars, signs
everything and uploads one bundle to Central.

By default the upload is **not published automatically**: open the
[Central Portal → Deployments](https://central.sonatype.com/publishing/deployments), check that it says
*Validated*, and press **Publish**. This is a safety net for the first releases, because a published version can
never be changed or deleted. Once you trust the pipeline, set `<autoPublish>true</autoPublish>` in the `central`
profile of the root `pom.xml`.

The artifact appears on <https://repo.maven.apache.org/maven2/io/github/djamelkorei/> within about 30 minutes
and in search somewhat later.

## What gets published

`looma-parent`, `looma-core` and `looma-spring-boot-starter`. The demo, integration-test and benchmark modules
set `skipPublishing`, so they are never uploaded.

## Trying the pipeline without publishing

```bash
./mvnw -Prelease,central verify -DskipTests     # signs everything (needs MAVEN_GPG_KEY / MAVEN_GPG_PASSPHRASE in the env)
```

Without Central credentials `deploy` stops before uploading.

## IntelliJ plugin

The plugin is released separately from the engine, to [JetBrains Marketplace](https://plugins.jetbrains.com),
by `.github/workflows/intellij-release.yml`. Its version is `pluginVersion` in `looma-intellij/gradle.properties`.

### One-time setup

1. **The first upload is manual.** Marketplace only accepts a new plugin through the website. Build the zip with
   `./gradlew buildPlugin` in `looma-intellij` (it lands in `build/distributions/`), open
   <https://plugins.jetbrains.com/plugin/add>, upload it, choose the licence (Apache 2.0) and submit. JetBrains
   reviews a new plugin by hand, usually within two business days. Later versions upload automatically.
2. **A publishing token.** <https://plugins.jetbrains.com/author/me/tokens> → *Generate token*.
3. **A signing key** (optional, recommended: Marketplace shows signed plugins as verified).

    ```bash
    openssl genpkey -aes-256-cbc -algorithm RSA -out private.pem -pkeyopt rsa_keygen_bits:4096
    openssl req -key private.pem -new -x509 -days 3650 -out chain.crt -subj "/CN=Djamel Eddine Korei"
    ```

4. **Repository secrets.**

    ```bash
    gh secret set JETBRAINS_PUBLISH_TOKEN        -R djamelkorei/looma                 # from step 2
    gh secret set JETBRAINS_CERTIFICATE_CHAIN    -R djamelkorei/looma < chain.crt     # step 3, optional
    gh secret set JETBRAINS_PRIVATE_KEY          -R djamelkorei/looma < private.pem   # step 3, optional
    gh secret set JETBRAINS_PRIVATE_KEY_PASSWORD -R djamelkorei/looma                 # the passphrase from step 3
    rm private.pem chain.crt                                                          # or keep them in a password manager
    ```

### Cutting a plugin release

1. Set `pluginVersion` in `looma-intellij/gradle.properties` and add the version to `<change-notes>` in
   `looma-intellij/src/main/resources/META-INF/plugin.xml`.
2. Commit, then tag and push:

    ```bash
    git tag intellij-v0.1.0 && git push origin intellij-v0.1.0
    ```

The workflow refuses a tag that does not match `pluginVersion`, runs the tests and the compatibility verifier,
signs and uploads the plugin, and attaches the zip to a GitHub release of the same name. A version such as
`0.2.0-beta` goes to the Marketplace *beta* channel, which only users who added it see.

To try the upload step locally: `PUBLISH_TOKEN=... ./gradlew publishPlugin`.

## If something goes wrong

* **"Invalid signature" / "key not found" in the portal:** the public key is not on a keyserver yet (step 3), or it
  needs a few minutes to propagate.
* **"Namespace not verified":** finish step 1.
* **A published version is wrong:** it cannot be removed. Fix it and release the next version.
