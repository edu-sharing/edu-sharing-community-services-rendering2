FROM ${docker.from.node.24}

# apk upgrade: pick up OS package fixes the base image has not been rebuilt with yet.
# npm/npx are removed: the service only runs `node` and the npm bundled with the base image
# ships vulnerable undici and brace-expansion versions that are not needed at runtime.
RUN set -eux \
    && apk update  \
    && apk upgrade --no-cache \
    && apk add --no-cache netcat-openbsd \
    && rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx

WORKDIR /application

ENV PATH /application/bin:$PATH

COPY assets/entrypoint.sh bin/entrypoint.sh

RUN set -eux \
	  && find bin -type f -name '*.sh' -exec chmod +x {} \;

# Mount point for the per-package H5P library cache (H5P_LIBRARY_CACHE=package). It has to exist in
# the image, owned by the user the container runs as: /application itself is root-owned, so a volume
# mounted here would otherwise be created as root and the node user could not write into it. Docker
# initialises a fresh named volume with this directory's ownership; on Kubernetes the kubelet handles
# it via the pod's fsGroup.
RUN set -eux \
    && mkdir -p /application/library-cache \
    && chown node:node /application/library-cache

USER node

#TODO aktivate profiling...

COPY --chown=node:node artifacts ./
ENTRYPOINT ["entrypoint.sh", "node ${NODE_OPTS} dist/index.js"]

EXPOSE 3000
