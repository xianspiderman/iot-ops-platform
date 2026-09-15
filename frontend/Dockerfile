FROM node:24-alpine AS build
WORKDIR /web
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:24-alpine
WORKDIR /web
COPY --from=build /web/dist ./dist
COPY server.mjs ./server.mjs
USER node
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=10 CMD wget -q -O - http://127.0.0.1:8080/ >/dev/null || exit 1
CMD ["node", "server.mjs"]
