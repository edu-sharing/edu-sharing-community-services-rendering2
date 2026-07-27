FROM ${docker.from.node.24}

WORKDIR /application

# Statischer SPA-Server (nur Node-Bordmittel)
COPY assets/server.mjs /server.mjs

# Statischer Angular-Build (Inhalt von dist/admin/browser)
COPY --chown=node:node artifacts ./

USER node

ENTRYPOINT ["node", "/server.mjs"]

EXPOSE 8080
