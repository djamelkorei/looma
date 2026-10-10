# Security policy

## Reporting a vulnerability

Please **do not open a public issue**. Use GitHub's private reporting:
[Report a vulnerability](https://github.com/djamelkorei/looma/security/advisories/new). You will get an answer
within a week. Fixes are released as a new patch version and credited in the advisory unless you prefer otherwise.

## Supported versions

Looma is pre-1.0: only the latest released version receives fixes.

## Threat model in one paragraph

Templates are **trusted code** written by the application's developers. Data passed to templates is untrusted
and is never evaluated: `{{ }}` escapes it, and expressions run in a sandbox (no `T()`, constructors, bean
references, `class`/`getClass()`, or access to `Class`, `ClassLoader`, reflection, `Thread`, `Process`...) as
defense in depth. Looma is not designed to run templates written by end users. Details:
[Security & operations](https://djamelkorei.github.io/looma/security/).

## Dependencies

Looma's Spring dependencies are resolved by your application's Spring Boot version, so keep Spring Boot up to
date. Advisories in Spring Framework that concern features Looma does not use (for example
CVE-2026-47884 in `XsltView` and CVE-2026-47890 in SSE fragment rendering, both in spring-webmvc 6.2.19) do not
affect Looma's own code, but still apply to your application if you use those features.
