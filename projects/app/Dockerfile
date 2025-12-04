FROM clojure:temurin-25-trixie-slim

WORKDIR /dist

RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y curl && \
    apt-get clean

# Install nodejs
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && \
    apt-get install -y nodejs && \
    apt-get clean

RUN mkdir bin && \
    curl -fsSL -o bin/migrate.sh https://raw.githubusercontent.com/brettatoms/sqlite-migrate/main/migrate.sh && \
    chmod +x bin/migrate.sh

# Copy specific files and directories to avoid any working files we might have
# in the root directory that we missed in the .dockerignore
COPY deps.edn /dist
COPY db /dist/db
COPY components /dist/components
COPY bases /dist/bases
COPY projects /dist/projects
COPY bases/vite.config.js /dist

RUN chmod +x /dist/projects/app/run.sh

# Don't build the assets when starting the system since we're building them in advance
ENV BUILD_ASSETS=false

RUN cd /dist/bases/app && \
    npm install && \
    npm run build && \
    cd /dist/projects/app && \
    clojure -P -M:main

EXPOSE 3000

# TODO: WE should turn this into a multistage build

CMD /dist/projects/app/run.sh
