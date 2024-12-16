FROM ${docker.from.node.21}

RUN set -eux \
    && apk update  \
    && apk add --no-cache netcat-openbsd

WORKDIR /application

ENV PATH /application/bin:$PATH

COPY assets/entrypoint.sh bin/entrypoint.sh

RUN set -eux \
	  && find bin -type f -name '*.sh' -exec chmod +x {} \;

USER node

#TODO aktivate profiling...

COPY --chown=node:node artifacts ./
ENTRYPOINT ["entrypoint.sh", "exec node ${NODE_OPTS} dist/index.js"]

EXPOSE 3000
