FROM ${docker.from.node.21}

RUN set -eux \
    && apk update  \
    && apk add --no-cache netcat-openbsd

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
