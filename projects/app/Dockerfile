FROM clojure:temurin-22-bookworm-slim

WORKDIR /dist

RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y curl && \
    apt-get clean

# Install nodejs
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    apt-get clean

# Copy specific files and directories to avoid any working files we might have
# in the root directory that we missed in the .dockerignore
COPY deps.edn /dist
COPY components /dist/components
COPY bases /dist/bases
COPY projects /dist/projects

RUN clojure -P -M:migrations && \
    cd /dist/bases/app && \
    npm install && \
    npm run build && \
    cd /dist/projects/app && \
    clojure -P -M:main

EXPOSE 3000

CMD /dist/projects/app/run.sh
