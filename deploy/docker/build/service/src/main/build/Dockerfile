FROM ${docker.from.openjdk.21} as builder
WORKDIR application
ARG JAR_FILE=artifacts/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted && ls -la extracted
RUN mkdir -p extracted/snapshot-dependencies && if [[ -z "$(ls -A ./extracted/snapshot-dependencies)" ]] ; then touch ./extracted/snapshot-dependencies/.keep; fi


FROM ${docker.from.eclipse-temurin.21}

RUN set -eux \
    && apk update  \
    && apk add --no-cache netcat-openbsd curl ffmpeg


VOLUME /tmp
WORKDIR application

ENV PATH /application/bin:$PATH


COPY assets/entrypoint.sh bin/entrypoint.sh

RUN set -eux \
	  && find bin -type f -name '*.sh' -exec chmod +x {} \; \
    && adduser --uid=1000 --home=/application --disabled-password --gecos "" --shell=/bin/bash worker \
    && chown -RL worker:worker . \
    && chown -RL worker:worker /tmp


USER worker

COPY --from=builder application/extracted/dependencies/ ./
COPY --from=builder application/extracted/spring-boot-loader/ ./
COPY --from=builder application/extracted/snapshot-dependencies/ ./
COPY --from=builder application/extracted/application/ ./

########################################################################################################################

# The download is pinned by checksum, so a changed or tampered release asset fails the build instead of ending up in the image.
ARG ASYNC_PROFILER_SHA256=86b6e68c26ae62f29d9325ceee846694b2ec36437831e29066e618ecb9948446
RUN set -eux \
    && mkdir profiler \
    && wget -q -O /tmp/async-profiler.tar.gz https://github.com/Granulate/async-profiler/releases/download/v2.0g1/async-profiler-2.0-linux-x64.tar.gz \
    && echo "${ASYNC_PROFILER_SHA256}  /tmp/async-profiler.tar.gz" | sha256sum -c - \
    && tar xz -f /tmp/async-profiler.tar.gz -C profiler \
    && rm -f /tmp/async-profiler.tar.gz

COPY --chown=worker:worker assets/profiler profiler/

########################################################################################################################

EXPOSE 8080
ENTRYPOINT ["entrypoint.sh", "java ${JAVA_OPTS} --enable-preview -Dspring.profiles.active=docker org.springframework.boot.loader.launch.JarLauncher"]

########################################################################################################################

LABEL git.branch=${git.branch} \
git.closest.tag.name=${git.closest.tag.fixed} \
git.commit.id=${git.commit.id} \
git.dirty=${git.dirty} \
mvn.project.artifactId=${project.artifactId} \
mvn.project.groupId=${project.groupId} \
mvn.project.version=${project.version}
