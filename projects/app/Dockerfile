# Stage 1: Build frontend assets
FROM node:22-slim AS frontend-builder

WORKDIR /build

# Copy package files and install dependencies
COPY bases/app/package*.json ./bases/app/
RUN cd bases/app && npm ci

# Copy all frontend source files and build
COPY bases/app ./bases/app
RUN cd bases/app && npm run build

# Stage 2: Build uberjar
FROM clojure:temurin-25-trixie-slim AS clojure-builder

WORKDIR /build

# Copy project files needed for dependency resolution and building
COPY deps.edn ./
COPY components ./components
COPY bases ./bases
COPY projects ./projects

# Copy built frontend assets into the build context
COPY --from=frontend-builder /build/bases/app/resources/app/build \
    ./bases/app/resources/app/build

# Download dependencies and build uberjar
RUN cd projects/app && clojure -T:build uber

# Stage 3: Runtime image
FROM eclipse-temurin:25-jre-noble

WORKDIR /app

# Install SpatiaLite and sqlite3 for migrations and geo-coordinate support
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

# Copy application files
COPY db ./db
COPY bin/lib ./bin/lib
COPY projects/app/run.sh ./run.sh

# Don't rebuild assets at startup (they're pre-built in the uberjar)
ENV BUILD_ASSETS=false

# Copy the uberjar from builder
COPY --from=clojure-builder /build/projects/app/target/sepal.jar ./sepal.jar

RUN chmod +x run.sh

EXPOSE 3000

CMD ["./run.sh"]
