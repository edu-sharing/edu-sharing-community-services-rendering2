import {IPlayerModel} from "@lumieducation/h5p-server";

/**
 * Core player styles that the upstream h5p-php-library ships but the bundled
 * @lumieducation/h5p-server playerAssetList.json omits. Kept in sync with the
 * corrected upstream list (H5P-Nodejs-library
 * https://github.com/Lumieducation/H5P-Nodejs-library/blob/master/packages/h5p-server/src/playerAssetList.json
 * packages/h5p-server/src/playerAssetList.json):
 *
 *   styles/h5p-fonts.css
 *   styles/h5p-theme.css
 *   styles/h5p-theme-variables.css
 */
const REQUIRED_CORE_STYLES = [
    'h5p-fonts.css',
    'h5p-theme.css',
    'h5p-theme-variables.css',
];

/**
 * URLs for the required core styles that are not already present in the model,
 * derived from the base path of the core `h5p.css` link so they follow the
 * configured `BASE_URL`/core URL and are served from the downloaded core
 * (h5p/core/styles). Returns nothing (and adds no duplicates) once a future
 * h5p-server release lists these itself.
 */
const missingCoreStyles = (model: IPlayerModel): string[] => {
    const coreStyle = model.styles.find((s) => /\/styles\/h5p\.css(\?.*)?$/.test(s));
    if (!coreStyle) return [];
    const base = coreStyle.replace(/h5p\.css(\?.*)?$/, ''); // e.g. /public/h5p/core/styles/
    return REQUIRED_CORE_STYLES
        .map((name) => `${base}${name}`)
        .filter((url) => !model.styles.includes(url));
};

/**
 * Custom H5P player for Edu-Sharing purposes
 */
export default (model: IPlayerModel): string => `<!doctype html>
<html class="h5p-iframe">
<head>
    <meta charset="utf-8">
   
    ${[...missingCoreStyles(model), ...model.styles]
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
