# syntax=docker/dockerfile:1.6

FROM eclipse-temurin:21-jre AS downloader
ARG VELOCITY_JAR_URL
ARG VIAVERSION_JAR_URL
RUN test -n "$VELOCITY_JAR_URL" || (echo "VELOCITY_JAR_URL build arg is required" && exit 1)
RUN test -n "$VIAVERSION_JAR_URL" || (echo "VIAVERSION_JAR_URL build arg is required" && exit 1)
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/velocity
RUN curl -fsSL "$VELOCITY_JAR_URL" -o velocity-proxy.jar
RUN mkdir -p /opt/plugins && curl -fsSL "$VIAVERSION_JAR_URL" -o /opt/plugins/ViaVersion.jar

FROM eclipse-temurin:21-jre
ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends tini gettext-base \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/velocity
COPY --from=downloader /opt/velocity/velocity-proxy.jar /opt/velocity/velocity-proxy.jar
COPY server/start.sh /usr/local/bin/start-velocity.sh
COPY templates /opt/templates
COPY plugins /opt/plugins
COPY --from=downloader /opt/plugins/ViaVersion.jar /opt/plugins/ViaVersion.jar
RUN sed -i '1s/^\xEF\xBB\xBF//;s/\r$//' /usr/local/bin/start-velocity.sh \
    && find /opt/templates -type f -name '*.tpl' -exec sed -i '1s/^\xEF\xBB\xBF//;s/\r$//' {} + \
    && chmod +x /usr/local/bin/start-velocity.sh
ENV DATA_DIR=/data \
    TEMPLATE_DIR=/opt/templates \
    JAVA_OPTS="-Xms512M -Xmx512M" \
    SERVER_PORT=25577
VOLUME ["/data"]
EXPOSE 25577
ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/start-velocity.sh"]
