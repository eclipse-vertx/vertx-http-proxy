FROM fedora:43 AS base

RUN dnf install -y nodejs npm && dnf clean all

WORKDIR /app

COPY package.json ./
RUN npm install

COPY test.proto ./

FROM base AS client

COPY client.js ./

CMD ["node", "client.js"]
