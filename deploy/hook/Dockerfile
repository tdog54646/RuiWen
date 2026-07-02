ARG DOCKER_CLI_IMAGE=docker:27-cli
FROM ${DOCKER_CLI_IMAGE}

ARG ALPINE_MIRROR=https://dl-cdn.alpinelinux.org/alpine
RUN sed -i "s|https://dl-cdn.alpinelinux.org/alpine|${ALPINE_MIRROR}|g" /etc/apk/repositories \
    && apk add --no-cache python3 tar gzip

WORKDIR /app
COPY deploy/hook/app.py /app/app.py

EXPOSE 8989
CMD ["python3", "/app/app.py"]
