# admin-frontend — CLAUDE.md

Module-local guidance for the **admin frontend**. See the repo-root [`../CLAUDE.md`](../CLAUDE.md)
for build/versioning context and [`../service/CLAUDE.md`](../service/CLAUDE.md) for the
`/admin` REST API this app consumes.

## What this module is
A standalone **Angular 21** single-page admin UI for the rendering service (storage usage,
job management, repository details, S3 asset management). It is **not** embedded in the
Spring service — like `lumi` it ships as its own container (a tiny static Node server) and
is reached same-origin behind the proxy.

## Layout
- Angular workspace lives under **`src/main/frontend/`** (Maven convention; the `frontend-maven-plugin`
  runs there via `workingDirectory`). App project name: `admin`. Build output: `dist/admin/browser`.
- `pom.xml` + `bin.xml`: `frontend-maven-plugin` (`install-node-and-npm` → `npm install` →
  `ng build`) then assembly-zip of the **static** `dist/admin/browser` into a `bin.zip`.
- **Node version is pinned to `22.17.0`** (`pom.xml` properties) because Angular 21 needs
  Node ≥ 20.19/22.12/24 and that version is hosted on the Artifactory raw mirror
  (`raw/nodejs.org/dist`). To bump Node, upload the new version to that mirror first.

## Build & run
This module builds with the **system `mvn`** (not `./service/mvnw`):
```bash
mvn -Pdev -pl admin-frontend clean package        # node + npm ci + ng build + bin.zip
# inside src/main/frontend for fast local iteration:
cd admin-frontend/src/main/frontend
npx ng build            # production build
npx ng serve            # dev server :4200 (uses proxy.conf.json)
```
- **Dev proxy** (`src/main/frontend/proxy.conf.json`): forwards `/admin` → the backend.
  The app calls **relative `/admin/**`** in dev (no runtime config present), so the proxy
  `pathRewrite`/target must point at wherever the backend serves `/<context>/admin` (Angular's
  Vite dev server does not reliably prepend a path in `target`, hence the explicit `pathRewrite`).

## Architecture
- **repoId is the "große Klammer".** `RepoContextService` holds exactly one active `repoId`
  (global topbar selector, persisted in localStorage). Every feature view reads it and scopes
  all API calls to it — the UI never shows more than one repoId at a time.
- **Feature views** (lazy standalone components under `src/app/features/`): `dashboard`,
  `assets`, `jobs`, `repo`, plus `login`. Routes + `authGuard` in `app.routes.ts`.
- **`core/`** services: `AdminApiService` (typed client for `/admin/**`), `AuthService`
  (HTTP-Basic of the single admin user, in sessionStorage) + `authInterceptor` (adds the
  header, redirects to login on 401), `RepoContextService`, `SettingsService`
  (configurable poll interval, persisted), `PollingService` (reactive tick stream, pauses on
  hidden tab), `NotificationService` (MatSnackBar wrapper). Models in `core/models.ts` mirror
  the backend DTOs exactly (hand-maintained — could be OpenAPI-generated later).
- **`shared/data-table.ts`**: the one reusable table (client-side search + per-column sort +
  row-action template + expandable-row template). **Intentionally non-generic / loosely typed**
  (`any` accessors) because Angular can't infer a component's generic type argument from inputs
  in templates — keep it that way.

## Runtime configuration (no rebuild per environment)
`ADMIN_API_BASE` is **not** compiled in. It is read at runtime from
`window.RS2_ADMIN_CONFIG.apiBase` (default `''`), which the static server injects into
`index.html`. Likewise `<base href>` is set at runtime. See the container's `server.mjs`
(`deploy/docker/build/admin-frontend/src/main/build/assets/server.mjs`), driven by the
`BASE_HREF` and `ADMIN_API_BASE` env vars. This lets one image run behind nginx-proxy
(`VIRTUAL_*`) or an external Apache2 config.

## Angular / Material conventions
- **Angular 21**, standalone components, signals, new control-flow (`@if`/`@for`/`@switch`),
  `inject()` over constructor params, `input()`/`output()` signal APIs.
- **Angular Material (M3)**. The theme palette is generated from the edu-sharing seed colors
  (`src/theme-colors.scss`, primary `#25364f` / tertiary `#f29400`) and applied in
  `src/styles.scss` — do **not** reintroduce the default azure/orange palettes (that leaks
  blue into derived tokens). The brand-navy topbar is pinned via `mat-toolbar.topbar`.
- **Fonts are hosted locally — never add a Google Fonts / CDN link.** Roboto + Material Icons
  come from `@fontsource/*` (wired in `angular.json` styles); the `.material-icons` ligature
  class is defined in `styles.scss` (`@fontsource` only ships the `@font-face`).
- Custom chrome uses `--es-*` CSS variables (`styles.scss`); Material components use the
  `--mat-sys-*` tokens. For destructive buttons use `class="warn-btn"` (locally remaps
  `--mat-sys-primary` to the danger color).

## Deployment
Built artifact → `deploy/docker/build/admin-frontend` (node:21-alpine + `server.mjs`,
static SPA server with `/ping` health and prefix-stripping) → Helm subchart
`deploy/docker/helm/admin-frontend` + bundle wiring + the `rendering2-admin-frontend`
compose service. See [`../deploy/CLAUDE.md`](../deploy/CLAUDE.md).
