# document-converter — CLAUDE.md

Module-local guidance. Repo-root [`../CLAUDE.md`](../CLAUDE.md) covers build/versioning conventions.

## What this module is
A small Spring Boot REST service that converts Office documents to HTML/PDF using
**jodconverter** (4.4.11) driving a **local LibreOffice** process. The main `service`
module calls it at `app.documentConverter.host` for its `document` rendering module.

Kotlin 2.3, package root
`org.edu_sharing.edusharingcommunityservicesdocumentconverter`.

## ⚠️ Spring Boot version is pinned to 3.5.14 — do not bump to Boot 4
`pom.xml` pins `<spring-boot.version>3.5.14</spring-boot.version>` deliberately: jodconverter
has no Spring Boot 4 release. This is the one module in the repo intentionally off Boot 4.
Leave it on 3.5.x until jodconverter ships Boot-4 support.

## Layout
| Path | Responsibility |
|---|---|
| `controller/ConversionController.kt` | `@RestController @RequestMapping("/conversion")`, `POST` multipart upload with optional `format` param (default PDF). |
| `service/ConversionService.kt` | Runs the jodconverter conversion; jsoup post-processes HTML output (adds table CSS styling). |
| `config/ConverterConfig.kt` | Wires the jodconverter `LocalConverter` bean. |
| `exception/` | `FormatException` (→ 412), `ApiExceptionHandler`; missing file → 400. |
| `dto/ErrorMessage.kt` | Error response body. |

## Behavior & config (`src/main/resources/application.properties`)
- Source extensions allowed via `app.supportedExtensions`:
  `doc, docx, ppt, pptx, xls, xlsx, odt, odp, ods, txt, ott, rtf, csv`.
- `jodconverter.local.enabled=true`.
- Multipart limit 128 MB (`spring.servlet.multipart.max-file-size/-request-size`).
- Actuator on **port 8081** (`management.server.port=8081`, exposing `health,metrics,prometheus`).
  The main web server uses the Spring default unless overridden by the deployment.
- HTML output is the special case: converted HTML is re-styled with jsoup; other target
  formats pass through jodconverter directly.

## Runtime dependency
LibreOffice **must be installed** on the host running this service — the jodconverter
`LocalConverter` launches it as a subprocess. The Docker image
(`deploy/docker/build/document-converter`) installs LibreOffice plus fonts
(fontconfig, ttf-dejavu, msttcorefonts); local runs need a local LibreOffice.

## Build
This module builds with the system **`mvn`** (not the `./service/mvnw` wrapper):

```bash
mvn -Pdev -pl document-converter clean compile
mvn -Pdev -pl document-converter clean verify
```
No unit tests exist in this module yet — if you add behavior, add tests under `src/test`.
