FROM ubuntu:24.04 AS base

ENV LANG='C.UTF-8' \
    LC_ALL='C.UTF-8' \
    DEBIAN_FRONTEND=noninteractive

RUN set -ex; \
    apt-get -qq update; \
    apt-get -qq -y upgrade; \
    apt-get -qqy --no-install-recommends install \
        autoconf \
        bash \
        binutils \
        build-essential \
        ca-certificates \
        curl \
        git \
        libtool \
        pkg-config \
        python3 \
        rsync \
        sudo \
        tmux \
        unzip \
        vim \
        wget \
    ; \
    rm -rf /var/lib/apt/lists/*

################################################################################
## NODE SETUP
################################################################################

FROM base AS setup-node

ENV NODE_VERSION=v22.21.1 \
    PATH=/opt/node/bin:$PATH

RUN set -eux; \
    ARCH="$(dpkg --print-architecture)"; \
    case "${ARCH}" in \
       aarch64|arm64) \
         OPENSSL_ARCH='linux-aarch64'; \
         BINARY_URL="https://nodejs.org/dist/${NODE_VERSION}/node-${NODE_VERSION}-linux-arm64.tar.gz"; \
         ;; \
       amd64|x86_64) \
         OPENSSL_ARCH='linux-x86_64'; \
         BINARY_URL="https://nodejs.org/dist/${NODE_VERSION}/node-${NODE_VERSION}-linux-x64.tar.gz"; \
         ;; \
       *) \
         echo "Unsupported arch: ${ARCH}"; \
         exit 1; \
         ;; \
    esac; \
    curl -LfsSo /tmp/nodejs.tar.gz ${BINARY_URL}; \
    mkdir -p /opt/node; \
    cd /opt/node; \
    tar -xf /tmp/nodejs.tar.gz --strip-components=1; \
    chown -R root /opt/node; \
    find /opt/node/include/node/openssl/archs -mindepth 1 -maxdepth 1 ! -name "$OPENSSL_ARCH" -exec rm -rf {} \; ; \
    corepack enable; \
    rm -rf /tmp/nodejs.tar.gz;


################################################################################
## CADDYSERVER SETUP
################################################################################

FROM base AS setup-caddy

ENV CADDY_VERSION=2.10.2

RUN set -eux; \
    ARCH="$(dpkg --print-architecture)"; \
    case "${ARCH}" in \
       aarch64|arm64) \
         BINARY_URL="https://github.com/caddyserver/caddy/releases/download/v${CADDY_VERSION}/caddy_${CADDY_VERSION}_linux_arm64.tar.gz"; \
         ;; \
       amd64|x86_64) \
         BINARY_URL="https://github.com/caddyserver/caddy/releases/download/v${CADDY_VERSION}/caddy_${CADDY_VERSION}_linux_amd64.tar.gz"; \
         ;; \
       *) \
         echo "Unsupported arch: ${ARCH}"; \
         exit 1; \
         ;; \
    esac; \
    curl -LfsSo /tmp/caddy.tar.gz ${BINARY_URL}; \
    mkdir -p /tmp/caddy; \
    cd /tmp/caddy; \
    tar -xf /tmp/caddy.tar.gz; \
    chown -R root /tmp/caddy; \
    mv /tmp/caddy/caddy /usr/bin/; \
    rm -rf /tmp/caddy.tar.gz; \
    rm -rf /tmp/caddy;

################################################################################
## JVM SETUP
################################################################################

FROM base AS setup-jvm

ENV CLOJURE_VERSION=1.12.3.1577

RUN set -eux; \
    ARCH="$(dpkg --print-architecture)"; \
    case "${ARCH}" in \
       aarch64|arm64) \
         ESUM='8c5321f16d9f1d8149f83e4e9ff8ca5d9e94320b92d205e6db42a604de3d1140'; \
         BINARY_URL='https://cdn.azul.com/zulu/bin/zulu25.30.17-ca-jdk25.0.1-linux_aarch64.tar.gz'; \
         ;; \
       amd64|x86_64) \
         ESUM='471b3e62bdffaed27e37005d842d8639f10d244ccce1c7cdebf7abce06c8313e'; \
         BINARY_URL='https://cdn.azul.com/zulu/bin/zulu25.30.17-ca-jdk25.0.1-linux_x64.tar.gz'; \
         ;; \
       *) \
         echo "Unsupported arch: ${ARCH}"; \
         exit 1; \
         ;; \
    esac; \
    curl -LfsSo /tmp/openjdk.tar.gz ${BINARY_URL}; \
    echo "${ESUM} */tmp/openjdk.tar.gz" | sha256sum -c -; \
    mkdir -p /opt/jdk; \
    cd /opt/jdk; \
    tar -xf /tmp/openjdk.tar.gz --strip-components=1; \
    rm -rf /tmp/openjdk.tar.gz;

RUN set -ex; \
    curl -LfsSo /tmp/clojure.sh "https://download.clojure.org/install/linux-install-${CLOJURE_VERSION}.sh"; \
    chmod +x /tmp/clojure.sh; \
    mkdir -p /opt/clojure; \
    /tmp/clojure.sh --prefix /opt/clojure; \
    rm -rf /tmp/clojure.sh;

################################################################################
## RUST SETUP
################################################################################

FROM base AS setup-rust

# Install Rust toolchain
ENV PATH=/opt/cargo/bin:$PATH \
    RUSTUP_HOME=/opt/rustup \
    CARGO_HOME=/opt/cargo \
    RUSTUP_VERSION=1.28.2 \
    RUST_VERSION=1.91.0 \
    EMSCRIPTEN_VERSION=4.0.6

WORKDIR /opt

RUN set -eux; \
    # Same steps as in Rust official Docker image https://github.com/rust-lang/docker-rust/blob/9f287282d513a84cb7c7f38f197838f15d37b6a9/1.81.0/bookworm/Dockerfile
    arch="$(dpkg --print-architecture)"; \
    case "$arch" in \
        'amd64') \
            rustArch='x86_64-unknown-linux-gnu'; \
            rustupSha256='20a06e644b0d9bd2fbdbfd52d42540bdde820ea7df86e92e533c073da0cdd43c'; \
            ;; \
        'arm64') \
            rustArch='aarch64-unknown-linux-gnu'; \
            rustupSha256='e3853c5a252fca15252d07cb23a1bdd9377a8c6f3efa01531109281ae47f841c'; \
            ;; \
    *) echo >&2 "unsupported architecture: ${dpkgArch}"; exit 1 ;; \
    esac; \
    wget "https://static.rust-lang.org/rustup/archive/${RUSTUP_VERSION}/${rustArch}/rustup-init"; \
    echo "${rustupSha256} *rustup-init" | sha256sum -c -; \
    chmod +x rustup-init; \
    ./rustup-init -y --no-modify-path --profile minimal \
        --default-toolchain "${RUST_VERSION}" \
        --default-host "${rustArch}"; \
    rm rustup-init; \
    rustup component add rustfmt; \
    rustup component add clippy; \
    git clone https://github.com/emscripten-core/emsdk.git; \
    cd emsdk; \
    ./emsdk install "${EMSCRIPTEN_VERSION}"; \
    ./emsdk activate "${EMSCRIPTEN_VERSION}"; \
    rustup target add wasm32-unknown-emscripten; \
    cargo install cargo-watch;

################################################################################
## UTILS SETUP
################################################################################

FROM base AS setup-utils

ENV CLJKONDO_VERSION=2025.07.28 \
    BABASHKA_VERSION=1.12.208 \
    CLJFMT_VERSION=0.13.1

RUN set -ex; \
    ARCH="$(dpkg --print-architecture)"; \
    case "${ARCH}" in \
       aarch64|arm64) \
         BINARY_URL="https://github.com/clj-kondo/clj-kondo/releases/download/v$CLJKONDO_VERSION/clj-kondo-$CLJKONDO_VERSION-linux-aarch64.zip"; \
         ;; \
       amd64|x86_64) \
         BINARY_URL="https://github.com/clj-kondo/clj-kondo/releases/download/v$CLJKONDO_VERSION/clj-kondo-$CLJKONDO_VERSION-linux-amd64.zip"; \
         ;; \
       *) \
         echo "Unsupported arch: ${ARCH}"; \
         exit 1; \
         ;; \
    esac; \
    cd /tmp; \
    curl -LfsSo /tmp/clj-kondo.zip ${BINARY_URL}; \
    mkdir -p /opt/utils/bin; \
    cd /opt/utils/bin; \
    unzip /tmp/clj-kondo.zip; \
    rm -rf /tmp/clj-kondo.zip;

RUN set -ex; \
    ARCH="$(dpkg --print-architecture)"; \
    case "${ARCH}" in \
       aarch64|arm64) \
         BINARY_URL="https://github.com/babashka/babashka/releases/download/v$BABASHKA_VERSION/babashka-$BABASHKA_VERSION-linux-aarch64-static.tar.gz"; \
         ;; \
       amd64|x86_64) \
         BINARY_URL="https://github.com/babashka/babashka/releases/download/v$BABASHKA_VERSION/babashka-$BABASHKA_VERSION-linux-amd64-static.tar.gz"; \
         ;; \
       *) \
         echo "Unsupported arch: ${ARCH}"; \
         exit 1; \
         ;; \
    esac; \
    cd /tmp; \
    curl -LfsSo /tmp/babashka.tar.gz ${BINARY_URL}; \
    cd /opt/utils/bin; \
    tar -xf /tmp/babashka.tar.gz; \
    rm -rf /tmp/babashka.tar.gz;

RUN set -ex; \
    ARCH="$(dpkg --print-architecture)"; \
    case "${ARCH}" in \
       aarch64|arm64) \
         BINARY_URL="https://github.com/weavejester/cljfmt/releases/download/${CLJFMT_VERSION}/cljfmt-${CLJFMT_VERSION}-linux-aarch64.tar.gz"; \
         ;; \
       amd64|x86_64) \
         BINARY_URL="https://github.com/weavejester/cljfmt/releases/download/${CLJFMT_VERSION}/cljfmt-${CLJFMT_VERSION}-linux-amd64.tar.gz"; \
         ;; \
       *) \
         echo "Unsupported arch: ${ARCH}"; \
         exit 1; \
         ;; \
    esac; \
    cd /tmp; \
    curl -LfsSo /tmp/cljfmt.tar.gz ${BINARY_URL}; \
    cd /opt/utils/bin; \
    tar -xf /tmp/cljfmt.tar.gz; \
    rm -rf /tmp/cljfmt.tar.gz;

# Install minio client
RUN set -ex; \
    ARCH="$(dpkg --print-architecture)"; \
    case "${ARCH}" in \
       aarch64|arm64) \
         BINARY_URL="https://dl.min.io/client/mc/release/linux-arm64/mc"; \
         ;; \
       amd64|x86_64) \
         BINARY_URL="https://dl.min.io/client/mc/release/linux-amd64/mc"; \
         ;; \
       *) \
         echo "Unsupported arch: ${ARCH}"; \
         exit 1; \
         ;; \
    esac; \
    wget -O /tmp/mc ${BINARY_URL}; \
    mv /tmp/mc /opt/utils/bin/; \
    chmod +x /opt/utils/bin/mc;


################################################################################
## DEVENV BASE
################################################################################

FROM base AS devenv-base

RUN set -ex; \
    usermod -l koda -d /home/koda -G users -s /bin/bash ubuntu; \
    passwd koda -d; \
    echo "koda ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers; \
    apt-get -qq update; \
    apt-get -qqy install --no-install-recommends \
      file \
      fontconfig \
      fontforge \
      fonts-freefont-ttf \
      fonts-ipafont-gothic \
      fonts-liberation \
      fonts-noto-color-emoji \
      fonts-tlwg-loma-otf \
      fonts-unifont \
      fonts-wqy-zenhei \
      gnupg2 \
      jq \
      less \
      libasound2t64 \
      libatk-bridge2.0-0t64 \
      libatk1.0-0t64 \
      libatspi2.0-0t64 \
      libcairo2 \
      libcups2t64 \
      libdbus-1-3 \
      libdrm2 \
      libfontconfig-dev \
      libfontconfig1 \
      libfreetype-dev \
      libfreetype6 \
      libgbm1 \
      libglib2.0-0 \
      libglib2.0-0t64 \
      libgomp1 \
      libheif1 \
      libjpeg-turbo8 \
      liblcms2-2 \
      libnspr4 \
      libnss3 \
      libopenexr-3-1-30 \
      libopenjp2-7 \
      libpango-1.0-0 \
      libpng16-16 \
      librsvg2-2 \
      libtiff6 \
      libwebp7 \
      libwebpdemux2 \
      libwebpmux3 \
      libx11-6 \
      libxcb1 \
      libxcomposite1 \
      libxdamage1 \
      libxext6 \
      libxfixes3 \
      libxkbcommon0 \
      libxml2 \
      libxrandr2 \
      libzip4t64 \
      libzstd1 \
      nginx \
      poppler-utils \
      python3-tabulate \
      redis-tools \
      rlwrap \
      woff-tools \
      woff2 \
      xfonts-cyrillic \
      xfonts-scalable \
      xvfb \
    ; \
    rm -rf /var/lib/apt/lists/*; \
    install -d /usr/share/postgresql-common/pgdg; \
    curl -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc --fail https://www.postgresql.org/media/keys/ACCC4CF8.asc; \
    echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt noble-pgdg main" >> /etc/apt/sources.list.d/postgresql.list; \
    apt-get -qq update; \
    apt-get -qqy install postgresql-client-16; \
    rm -rf /var/lib/apt/lists/*


################################################################################
## DEVENV
################################################################################

FROM devenv-base AS devenv
LABEL maintainer="Koda <docker@koda.app>"

ENV LANG='C.UTF-8' \
    LC_ALL='C.UTF-8' \
    DEBIAN_FRONTEND="noninteractive" \
    JAVA_HOME="/opt/jdk" \
    CARGO_HOME="/opt/cargo" \
    RUSTUP_HOME="/opt/rustup" \
    PATH="/opt/jdk/bin:/opt/utils/bin:/opt/clojure/bin:/opt/node/bin:/opt/imagick/bin:/opt/cargo/bin:$PATH"

COPY --from=penpotapp/imagemagick:7.1.2-0 /opt/imagick /opt/imagick
COPY --from=setup-jvm /opt/jdk /opt/jdk
COPY --from=setup-jvm /opt/clojure /opt/clojure
COPY --from=setup-node /opt/node /opt/node
COPY --from=setup-utils /opt/utils /opt/utils
COPY --from=setup-rust /opt/cargo /opt/cargo
COPY --from=setup-rust /opt/rustup /opt/rustup
COPY --from=setup-rust /opt/emsdk /opt/emsdk
COPY --from=setup-caddy /usr/bin/caddy /usr/bin/caddy

COPY files/nginx.conf /etc/nginx/nginx.conf
COPY files/nginx-mime.types /etc/nginx/mime.types
COPY files/phantomjs-mock /usr/bin/phantomjs

COPY files/bashrc         /root/.bashrc
COPY files/vimrc          /root/.vimrc
COPY files/tmux.conf      /root/.tmux.conf
COPY files/sudoers        /etc/sudoers

COPY files/Caddyfile           /home/
COPY files/selfsigned.crt      /home/
COPY files/selfsigned.key      /home/
COPY files/start-tmux.sh       /home/start-tmux.sh
COPY files/start-tmux-back.sh  /home/start-tmux-back.sh
COPY files/entrypoint.sh       /home/entrypoint.sh
COPY files/init.sh             /home/init.sh

ENTRYPOINT ["/home/entrypoint.sh"]
CMD ["/home/init.sh"]
