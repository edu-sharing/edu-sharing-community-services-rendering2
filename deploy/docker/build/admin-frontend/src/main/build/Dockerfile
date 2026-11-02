FROM ${docker.from.node.24}

# npm/npx are removed: the server only runs `node` and the npm bundled with the base image
# ships vulnerable undici and brace-expansion versions that are not needed at runtime.
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx

WORKDIR /application

# Statischer SPA-Server (nur Node-Bordmittel)
COPY assets/server.mjs /server.mjs

# Statischer Angular-Build (Inhalt von dist/admin/browser)
COPY --chown=node:node artifacts ./

USER node

ENTRYPOINT ["node", "/server.mjs"]

EXPOSE 8080
