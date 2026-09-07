# Deployment-Stack — Komponentenübersicht

Kurze, stichpunktartige Referenz, **wofür** die einzelnen Komponenten im Rendering2-Stack
zuständig sind und wie sie zusammenspielen. Bezug ist der **Dev-Stack** (docker-compose:
`deploy/docker/compose/src/main/compose/1_rendering2-common.yml` + `…-dev.yml`).

Ports/Hosts sind die Compose-Defaults (Service-interner Name → Port). Für die vollständige
Env-Var → Spring-Property-Zuordnung siehe [`../CLAUDE.md`](../CLAUDE.md).

## Infrastruktur

### MongoDB — `mongo-database` (27017)
- Primärdatenbank für Job-Zustand: persistiert `RenderingJob`/`SubJob` mit optimistischem
  `@Version` und TTL-Index (Aufräumen nach 8 Tagen).
- Hält die `Tracking`-Collection (enthält `repoId`) — Basis für die repoId-scoped Admin-Asset-Sicht.
- Zwei getrennte Datenbanken: `rendering` (Service) und `lumi` (H5P-Metadaten des lumi-Backends).

### Redis — `redis-cache` (6379)
- Spring-Session-Store (Jackson-3-Serialisierung), damit Sessions über Instanzen hinweg geteilt werden.
- Cache für Node-Permissions (Ablaufzeit je Modul via `app.session.<modul>.nodePermissionExpirationTime`).

### RabbitMQ — `rendering2-message-queue` (5672, Management-UI 15672)
- Nachrichten-Queue für **asynchrone** Render-Jobs: entkoppelt die schnelle Auftragsannahme
  von der teuren Konvertierung.
- Flow: Render-Auftrag → Queue → `JobReceiver` → modul-spezifischer SubJob-Receiver führt die
  Konvertierung aus.

### RustFS / S3-Storage — `rendering2-rustfs-storage` (9000, Konsole 9001)
- S3-kompatibler Objektspeicher für gerenderte Assets sowie H5P-Content.
- Bucket-Strategie standardmäßig `byType` (Ablage pro Media-Typ); wird vom Service (AWS SDK v2)
  und von lumi (eigene content-/temp-/lib-Buckets) genutzt.

## Services

### RS2 Service — `rendering2-service` (8080)
- Herzstück des Stacks: nimmt **signierte** Render-Requests von edu-sharing-Repositories an
  (`POST /public/renderdata`), prüft die Signatur gegen den Public Key des Repositories.
- Dispatcht per `ModuleRegistry` an das passende Content-Typ-Modul (Image, Video, Document,
  H5P, Jupyter, …) und rendert **synchron** oder als **Async-Job** (via RabbitMQ).
- Liefert die fertigen Assets aus (Download/Streaming inkl. HTTP-Range) und stellt die
  `/admin`-API für das admin-frontend bereit.

### document-converter — `rendering2-document-converter` (8080)
- Wandelt Office-Dokumente über LibreOffice/jodconverter in HTML/PDF um.
- Wird vom Service als Backend über `app.documentConverter.host` aufgerufen.

### jupyter-converter — `rendering2-jupyter-converter`
- Konvertiert Jupyter-Notebooks (`.ipynb`) via nbconvert nach HTML (FastAPI-Service).
- Wird vom Service über `app.jupyterConverter.host` angebunden.

### lumi — `rendering2-lumi` (3000)
- H5P-Editor-/Player-Backend; verwaltet H5P-Inhalte und -Bibliotheken.
- Nutzt eine eigene MongoDB (`lumi`) und eigene S3-Buckets (content/temp/lib).
- Wird vom Service über `app.lumi.host` angebunden.

### admin-frontend — `rendering2-admin-frontend` (8080)
- Angular-SPA für Administration (Storage-Nutzung, Jobs, Repository-Registrierung, Assets).
- Konsumiert die `/admin`-API des Service **same-origin** (ausgeliefert unter `/rendering-admin/`),
  daher kein CORS.
