# Stage 1: Build frontend assets
FROM node:22-slim AS frontend-builder

WORKDIR /build

# Copy package files and install dependencies
COPY bases/app/package*.json ./bases/app/
RUN cd bases/app && npm ci

# Copy all frontend source files and build
COPY bases/app ./bases/app
RUN cd bases/app && npm run build

# Stage 2: Prepare Clojure dependencies
FROM clojure:temurin-25-trixie-slim AS clojure-builder

WORKDIR /build
COPY deps.edn ./
COPY components ./components
COPY bases ./bases
COPY projects ./projects

# Download all dependencies
RUN cd projects/app && clojure -P -M:main

# Stage 3: Runtime image
FROM eclipse-temurin:25-jre-noble

WORKDIR /app

# Install SpatiaLite and curl (for migrate.sh)
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        libsqlite3-mod-spatialite \
        sqlite3 \
        curl \
        bash && \
    rm -rf /var/lib/apt/lists/* && \
    echo ".load mod_spatialite" > ~/.sqliterc

# Download migrate.sh
RUN mkdir -p bin && \
    curl -fsSL -o bin/migrate.sh \
        https://raw.githubusercontent.com/brettatoms/sqlite-migrate/main/migrate.sh && \
    chmod +x bin/migrate.sh

# Copy Maven cache from builder
COPY --from=clojure-builder /root/.m2 /root/.m2

# Copy application code
COPY deps.edn ./
COPY db ./db
COPY bin/lib ./bin/lib
COPY components ./components
COPY bases ./bases
COPY projects ./projects

# Copy built frontend assets
COPY --from=frontend-builder /build/bases/app/resources/app/build \
    ./bases/app/resources/app/build

# Don't rebuild assets at startup
ENV BUILD_ASSETS=false

RUN chmod +x projects/app/run.sh

EXPOSE 3000

CMD ["projects/app/run.sh"]
