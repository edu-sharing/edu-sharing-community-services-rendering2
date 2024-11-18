FROM ${docker.from.openjdk.21} as builder
WORKDIR application
ARG JAR_FILE=artifacts/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=layertools -jar application.jar extract && ls -la
RUN if [[ -z "$(ls -A ./snapshot-dependencies)" ]] ; then touch ./snapshot-dependencies/.keep; fi


FROM ${docker.from.eclipse-temurin.21}


VOLUME /tmp
WORKDIR application

RUN set -eux \
    && apt update -y \
    && apt install -y --no-install-recommends  \
      libreoffice \
    && apt clean \
    && rm -r /var/lib/apt/lists/*

RUN set -eux \
    && adduser --uid=1001 --home=/application --disabled-password --gecos "" --shell=/bin/bash worker \
    && chown -RL worker:worker . \
    && chown -RL worker:worker /tmp

USER worker
COPY --from=builder application/dependencies/ ./
COPY --from=builder application/spring-boot-loader/ ./
COPY --from=builder application/snapshot-dependencies/ ./
COPY --from=builder application/application/ ./

########################################################################################################################

RUN set -eux \
    && mkdir profiler \
    && wget -qO- https://github.com/Granulate/async-profiler/releases/download/v2.0g1/async-profiler-2.0-linux-x64.tar.gz | tar xvz -C profiler

COPY --chown=worker:worker assets/profiler profiler/

########################################################################################################################

EXPOSE 8080
ENTRYPOINT ["sh" ,"-c", "exec java ${JAVA_OPTS} --enable-preview -Dspring.profiles.active=docker org.springframework.boot.loader.launch.JarLauncher"]

########################################################################################################################

LABEL git.branch=${git.branch} \
git.closest.tag.name=${git.closest.tag.fixed} \
git.commit.id=${git.commit.id} \
git.dirty=${git.dirty} \
mvn.project.artifactId=${project.artifactId} \
mvn.project.groupId=${project.groupId} \
mvn.project.version=${project.version}
