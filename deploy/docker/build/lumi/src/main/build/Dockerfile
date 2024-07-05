FROM dockerio.mirror.docker.edu-sharing.com/node:21-alpine
WORKDIR /app

USER node

COPY --chown=node:node artifacts ./
ENTRYPOINT ["sh" ,"-c", "exec node ${NODE_OPTS} dist/index.js"]

EXPOSE 3000
