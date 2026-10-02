import express from 'express'
import path from 'path'
import {IPlayerModel} from '@lumieducation/h5p-server'

/**
 * LaTeX rendering for every H5P content, whether or not its package ships the `H5P.MathDisplay` addon.
 *
 * The old (PHP) rendering service injected MathDisplay plus a self-hosted MathJax into every H5P page,
 * so formulas rendered regardless of the content type or the package. With the stock player, math only
 * renders when the addon is in the library storage: H5P platforms add it to a package on export only
 * if they have it installed, the H5P Hub does not offer it, and in per-package library mode a package
 * only ever sees its own libraries. So lumi serves MathJax itself and the edu-sharing player injects it,
 * following the upstream addon (h5p-math-display 1.0.50: MathJax 4, same configuration, same
 * re-typesetting of content that H5P renders later).
 *
 * A package that does ship `H5P.MathDisplay` keeps using it - two MathJax instances on one page would
 * fight over `window.MathJax`.
 */

const MATHJAX_DIR = path.dirname(require.resolve('mathjax/package.json'))
const FONT_DIR = path.dirname(require.resolve('@mathjax/mathjax-newcm-font/package.json'))

/**
 * Both versions are in the URL, so the files can be cached for good: updating MathJax or its font
 * moves every asset to a new URL.
 */
const ASSETS_KEY = `${require('mathjax/package.json').version}-${require('@mathjax/mathjax-newcm-font/package.json').version}`

/** Path of the MathJax root, relative to `config.baseUrl`. */
export const MATHJAX_PATH = `/mathjax/${ASSETS_KEY}`

/**
 * `[fonts]` in MathJax' path syntax. MathJax 4 resolves its font as `[fonts]/mathjax-newcm-font` and
 * defaults `[fonts]` to cdn.jsdelivr.net, which is why it has to be served here and set explicitly.
 */
const FONTS_PATH = `${MATHJAX_PATH}/fonts`

/**
 * Serves MathJax and its default font (New Computer Modern) from node_modules.
 *
 * Mounted under `config.baseUrl`, so the rendering service proxies it like the H5P core files.
 */
export const mathJaxRouter = (): express.Router => {
    const router = express.Router()
    const options = {immutable: true, maxAge: '365d', index: false}
    router.use(`${FONTS_PATH}/mathjax-newcm-font`, express.static(FONT_DIR, options))
    router.use(MATHJAX_PATH, express.static(MATHJAX_DIR, options))
    return router
}

/**
 * The delimiters MathJax typesets by default: `$$...$$`, `\[...\]`, `\(...\)` and `\begin{...}`
 * environments. The upstream addon's `addTo` pattern minus its `.+`, which never matched a formula
 * spanning several lines.
 */
const LATEX = /\$\$[\s\S]+?\$\$|\\\[[\s\S]+?\\\]|\\\([\s\S]+?\\\)|\\begin\{[^}]+\}/

const containsLatex = (value: unknown): boolean => {
    if (typeof value === 'string') return LATEX.test(value)
    if (value && typeof value === 'object') return Object.values(value).some(containsLatex)
    return false
}

/** Matches the script URLs of a shipped addon, both `/libraries/...` and `/package-libraries/<id>/...`. */
const SHIPPED_MATH_DISPLAY = /\/H5P\.MathDisplay-\d+\.\d+\//

/**
 * Whether the page needs our MathJax: the content contains LaTeX, and does not bring the addon itself.
 *
 * Gating on the parameters mirrors the addon's `addTo`, and keeps MathJax (about 1 MB of script plus
 * fonts) off the vast majority of pages, which have no formula at all.
 */
const needsMathJax = (model: IPlayerModel): boolean => {
    if (model.scripts.some((script) => SHIPPED_MATH_DISPLAY.test(script))) {
        return false
    }
    const content = model.integration.contents?.[`cid-${model.contentId}`]
    if (!content?.jsonContent) {
        return false
    }
    try {
        return containsLatex(JSON.parse(content.jsonContent))
    } catch {
        return false
    }
}

/**
 * The `<head>` markup that loads MathJax into the player page, or an empty string if it is not needed.
 *
 * Configuration and observer follow the upstream addon (src/scripts/config.js and mathdisplay.js):
 * MathJax typesets the page once it is ready, and a MutationObserver re-typesets what content types
 * render later (slides, feedback, ...). Typesetting is chained onto `MathJax.startup.promise`, as
 * MathJax requires for calls that may overlap.
 */
export const mathDisplayHead = (model: IPlayerModel): string => {
    if (!needsMathJax(model)) {
        return ''
    }
    const root = `${model.integration.url}${MATHJAX_PATH}`
    return `<script>
      (function () {
        // Absolute, because MathJax starts its speech worker from a blob URL.
        var root = new URL(${JSON.stringify(root)}, document.baseURI).href;
        var delimiters = /\\$\\$|\\\\\\(|\\\\\\[|\\\\begin\\{/;
        var queued = [];
        var scheduled = null;

        function resize() {
          try {
            H5P.instances[0].trigger('resize');
          } catch (e) {
            // No H5P instance (yet): nothing to resize.
          }
        }

        function typeset() {
          var elements = queued;
          queued = [];
          scheduled = null;
          MathJax.startup.promise = MathJax.startup.promise
            .then(function () {
              MathJax.typesetClear(elements);
              return MathJax.typesetPromise(elements);
            })
            .then(function () {
              if (elements.some(function (element) { return element.querySelector('mjx-container'); })) {
                resize();
              }
            })
            .catch(function (error) {
              console.warn('MathJax typeset failed: ' + error.message);
            });
        }

        // Queues an element, unless it or an ancestor is already queued - MathJax fails on nested trees.
        function update(element) {
          if (queued.some(function (other) { return other.contains(element); })) return;
          queued = queued.filter(function (other) { return !element.contains(other); });
          queued.push(element);
          if (!scheduled) scheduled = setTimeout(typeset, 40);
        }

        window.MathJax = {
          loader: {
            paths: {
              mathjax: root,
              fonts: root + '/fonts'
            }
          },
          tex: {
            macros: {
              surfintegral: '\\u222F',
              volintegral: '\\u2230'
            }
          },
          chtml: {
            matchFontHeight: true,
            fontURL: root + '/fonts/mathjax-newcm-font/chtml/woff2',
            adaptiveCSS: true
          },
          options: {
            enableMenu: false,
            ignoreHtmlClass: 'ckeditor',
            processHtmlClass: 'tex2jax_process',
            enableExplorer: true,
            enableExplorerHelp: false,
            worker: {
              path: root + '/sre',
              maps: root + '/sre/mathmaps',
              worker: 'speech-worker.js'
            }
          },
          startup: {
            pageReady: function () {
              new MutationObserver(function (mutations) {
                mutations.forEach(function (mutation) {
                  var target = mutation.target;
                  // MathJax' own output must not trigger another round.
                  if (target.closest && target.closest('mjx-container')) return;
                  if (delimiters.test(target.textContent)) update(target);
                });
              }).observe(document.body, {childList: true, subtree: true});

              return MathJax.startup.defaultPageReady().then(resize);
            }
          }
        };
      })();
    </script>
    <script src="${root}/tex-chtml.js" defer></script>`
}
