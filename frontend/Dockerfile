FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
# Writes /config.js from env vars (e.g. GRAFANA_URL) each time the container starts.
# sed strips CRLF in case the script was checked out on Windows.
COPY docker-entrypoint.d/40-app-config.sh /docker-entrypoint.d/
RUN sed -i 's/\r$//' /docker-entrypoint.d/40-app-config.sh && chmod +x /docker-entrypoint.d/40-app-config.sh
