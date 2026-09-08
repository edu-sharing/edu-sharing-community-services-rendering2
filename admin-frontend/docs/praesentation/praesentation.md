---
marp: true
paginate: true
title: Rendering Admin – Stakeholder-Überblick
author: edu-sharing
---

<style>
:root {
  --navy: #25364f;
  --orange: #f29400;
}
section {
  background: #ffffff;
  color: #1f2733;
  font-family: Roboto, "Segoe UI", system-ui, sans-serif;
  font-size: 25px;
  padding: 56px 70px;
}
section h1 { color: var(--navy); font-size: 46px; }
section h2 {
  color: var(--navy);
  border-bottom: 4px solid var(--orange);
  padding-bottom: 8px;
  display: inline-block;
  margin-bottom: 24px;
}
section h3 { color: var(--navy); }
ul { line-height: 1.65; }
strong { color: var(--navy); }
code { color: var(--navy); background: #eef1f5; padding: 1px 6px; border-radius: 4px; }
footer { color: #8a96a8; }

/* Title / divider slides on brand navy */
section.lead {
  background: var(--navy);
  color: #fff;
  justify-content: center;
}
section.lead h1 { color: #fff; font-size: 62px; margin-bottom: 6px; }
section.lead h2 { color: var(--orange); border: none; font-weight: 500; font-size: 30px; }
section.lead p { color: #c4cdd9; }
section.lead strong { color: var(--orange); }

/* Feature slides with a screenshot on the right */
section.shot { font-size: 23px; }
section.shot ul { margin-top: 8px; }
</style>

<!-- _class: lead -->
<!-- _paginate: false -->

# Rendering Admin

## Verwaltungsoberfläche für den edu-sharing Rendering-Service

Stakeholder-Überblick · Juni 2026

---

## Worum geht es?

- Der **Rendering-Service** konvertiert und rendert Inhalte:
  Bilder, Audio/Video, Dokumente, H5P, Jupyter-Notebooks.
- Speicherverbrauch, Verarbeitungs-Jobs und Fehler waren bislang
  nur schwer einsehbar — der Betrieb war eine **Blackbox**.
- **Rendering Admin** macht diesen Zustand sichtbar — und steuerbar:
  eine schlanke Weboberfläche für den laufenden Betrieb.

---

## Auf einen Blick

- Eigenständige Weboberfläche (**Angular 21**), eigener Container,
  hinter dem bestehenden Proxy — same-origin erreichbar.
- Greift über die **`/admin`-Schnittstelle** des Services auf die Daten zu.
- **Ein aktives Repository** als durchgängiger Arbeitskontext
  (oben jederzeit umschaltbar).
- **Echtzeit** dank Auto-Refresh · barrierefrei nach **WCAG 2.1/2.2 AA**.

---

<!-- _class: shot -->

## Anmeldung

- Sichere Anmeldung über ein **Admin-Konto** (HTTP-Basic).
- Nach dem Login werden die verfügbaren
  **Repositories geladen** und zur Auswahl gestellt.

![bg right:56% fit](images/login.png)

---

<!-- _class: shot -->

## Dashboard — Zustand auf einen Blick

- **Echtzeit-Kennzahlen:** In queue · Failed · Finished · Total used.
- **Speicher-Auslastung** mit Ampel-Balken und Quota-Hinweis.
- **Drilldown** bis auf die einzelnen Storage-Buckets.

![bg right:58% fit](images/dashboard.png)

---

<!-- _class: shot -->

## Assets — Speicher gezielt verwalten

- **S3-Asset-Management** nach Typ und Knoten,
  inkl. Versionen und „last accessed".
- **Gezieltes Aufräumen:** löschen pro Typ, Knoten, Version
  oder alles — stets mit Bestätigung.
- **Filter** nach Typ, Zeitraum (von–bis) und Freitext.

![bg right:58% fit](images/assets.png)

---

<!-- _class: shot -->

## Jobs — Verarbeitung nachvollziehen

- Jeder Rendering-Job mit **Modul, Status, Node, Fehlertext**
  und aufklappbaren **Sub-Jobs** samt Fortschritt.
- Status-Badges: `QUEUED` · `PROCESSING` · `FINISHED` ·
  `FAILED` · `PARTIALLY_FAILED`.
- **Filter** nach Status und Zeitraum; alte Jobs verfallen
  automatisch (TTL).

![bg right:58% fit](images/jobs.png)

---

<!-- _class: shot -->

## Repository — zentrale Konfiguration

- **Auf einen Blick:** URL, Domains, Quota, Buckets,
  Signatur-Algorithmus, Public Key.
- **Aktivierte Module** (H5P, EduHTML, …) mit ihren
  CSP- und Credential-Einstellungen.
- **CORS-Konfiguration** inkl. letzter Synchronisation.

![bg right:56% fit](images/repository.png)

---

<!-- _class: shot -->

## Bedienkomfort & Barrierefreiheit

- **Repository-Umschalter** und einstellbares **Refresh-Intervall**
  (Off … 60 s; pausiert im Hintergrund-Tab).
- **Kontrast-Modus** Auto / High / Normal — **WCAG 2.1/2.2 AA**;
  der Marken-Look bleibt Standard.
- **Skip-Link** und Fokus-Management für Tastatur- und
  Screenreader-Nutzung.

![bg right:56% fit](images/contrast-high.png)

---

## Nutzen & Fazit

- **Transparenz** — Fehler und Speicherverbrauch sind sofort sichtbar.
- **Kontrolle** — gezieltes Aufräumen statt manueller Eingriffe im Hintergrund.
- **Fokus** — ein klares Werkzeug pro Repository, durchgängig barrierearm.
- **Einsatzbereit** — und offen für Feedback zu den nächsten Schritten.

---

<!-- _class: lead -->
<!-- _paginate: false -->

# Vielen Dank

## Fragen & Feedback willkommen

edu-sharing · Rendering Admin
