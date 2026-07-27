import {IPlayerModel} from "@lumieducation/h5p-server";

/**
 * WORKAROUND — TODO(remove once we run on a @lumieducation/h5p-server release
 * containing #4424 / commit 28e35fd8, "update H5P core from 1.27 to 1.28", which
 * adds these styles to playerAssetList.json). As of v10.0.5 that commit is in no
 * published tag, so no release lists them and the model omits them. Before
 * deleting this, grep the installed
 * node_modules/@lumieducation/h5p-server/build/src/playerAssetList.json for
 * `h5p-theme.css` to confirm the release ships it.
 *
 * The core player styles the upstream h5p-php-library ships but the bundled
 * playerAssetList.json (10.0.x) omits. The installed 10.0.4 list already carries
 * `h5p-table.css`, but we still name it here so older/other releases are covered;
 * the dedup guard below makes any already-listed style a no-op.
 *
 * Upstream core-style order (packages/h5p-server/src/playerAssetList.json after
 * #4424), which we mirror so the cascade matches:
 *   h5p-fonts.css, h5p.css, h5p-confirmation-dialog.css, h5p-core-button.css,
 *   h5p-theme.css, h5p-theme-variables.css, h5p-tooltip.css, h5p-table.css
 *
 * Each entry names the core style it must follow (`after`, first match wins) so
 * the injected style lands at its upstream slot — critically `h5p-theme.css`
 * must come *after* `h5p.css`/`h5p-core-button.css` to override the base rules.
 * `before` is a fallback anchor for styles that lead the list (`h5p-fonts.css`).
 */
const INJECTED_CORE_STYLES: { name: string; after: string[]; before?: string }[] = [
    {name: 'h5p-fonts.css', after: [], before: 'h5p.css'},
    {name: 'h5p-theme.css', after: ['h5p-core-button.css', 'h5p.css']},
    {name: 'h5p-theme-variables.css', after: ['h5p-theme.css', 'h5p-core-button.css', 'h5p.css']},
    {name: 'h5p-table.css', after: ['h5p-tooltip.css', 'h5p-theme-variables.css', 'h5p.css']},
];

/**
 * Returns `model.styles` with the core styles the bundled playerAssetList.json
 * omits injected at their upstream cascade positions. URLs are derived from the
 * base path of the core `h5p.css` link so they follow the configured
 * `BASE_URL`/core URL and are served from the downloaded core (h5p/core/styles).
 * A style already present is left untouched (no duplicates), so this returns the
 * model unchanged once a future h5p-server release lists these itself.
 */
const withCoreStyles = (model: IPlayerModel): string[] => {
    const coreStyle = model.styles.find((s) => /\/styles\/h5p\.css(\?.*)?$/.test(s));
    if (!coreStyle) return model.styles;
    const base = coreStyle.replace(/h5p\.css(\?.*)?$/, ''); // e.g. /public/h5p/core/styles/

    const styles = [...model.styles];
    const indexOf = (name: string): number =>
        styles.findIndex((s) => s === `${base}${name}` || s.startsWith(`${base}${name}?`));

    for (const {name, after, before} of INJECTED_CORE_STYLES) {
        if (indexOf(name) >= 0) continue; // already listed by the model -> no duplicate
        let at = -1;
        for (const anchor of after) {
            const i = indexOf(anchor);
            if (i >= 0) { at = i + 1; break; }
        }
        if (at < 0 && before) {
            const i = indexOf(before);
            if (i >= 0) at = i;
        }
        if (at < 0) at = 0;
        styles.splice(at, 0, `${base}${name}`);
    }
    return styles;
};

/**
 * Custom H5P player for Edu-Sharing purposes
 */
export default (model: IPlayerModel): string => `<!doctype html>
<html class="h5p-iframe">
<head>
    <meta charset="utf-8">
   
    ${withCoreStyles(model)
    .map((style) => `<link rel="stylesheet" href="${style}"/>`)
    .join('\n    ')}
    ${model.scripts
    .map((script) => `<script src="${script}"></script>`)
    .join('\n    ')}

    <script>
        window.H5PIntegration = ${JSON.stringify(model.integration, null, 2)};
    </script>

    <script>
      (function () {
        function onXapi(event) {
          let statement = event && event.data && (event.data.statement || event.data);
          if (!statement) return;
         
          if (window.parent && window.parent !== window) {
            window.parent.postMessage(
              {
                type: 'H5P_XAPI',
                contentId: ${JSON.stringify(model.contentId)},
                statement: statement
              },
              '*'
            );
          }
        }

        function attach() {
          if (!window.H5P || !window.H5P.externalDispatcher) return false;
          window.H5P.externalDispatcher.on('xAPI', onXapi);
          return true;
        }

        if (!attach()) {
          let interval = setInterval(function () {
            if (attach()) clearInterval(interval);
          }, 50);
          setTimeout(function () { clearInterval(interval); }, 5000);
        }
      })();
    </script>
    <style>
        ul.h5p-actions {
            display: none;
        }
    </style>
</head>
<body>
    <div class="h5p-content" data-content-id="${model.contentId}"></div>
</body>
</html>`;
