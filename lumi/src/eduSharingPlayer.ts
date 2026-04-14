import {IPlayerModel} from "@lumieducation/h5p-server";

/**
 * Custom H5P player for Edu-Sharing purposes
 */
export default (model: IPlayerModel): string => `<!doctype html>
<html class="h5p-iframe">
<head>
    <meta charset="utf-8">
   
    ${model.styles
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
