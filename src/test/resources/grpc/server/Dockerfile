FROM fedora:43 AS base

RUN dnf install -y nodejs npm && dnf clean all

WORKDIR /app

COPY package.json ./
RUN npm install

COPY test.proto ./

FROM base AS server

COPY server.js ./

EXPOSE 50051

CMD ["node", "server.js"]
