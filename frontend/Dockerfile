# syntax=docker/dockerfile:1

# ---------- build the static site ----------
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# The API address is baked into the bundle at build time: it must be reachable from the BROWSER, not from this container.
ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_CURRENCY=INR
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL VITE_CURRENCY=$VITE_CURRENCY
RUN npm run build

# ---------- serve it with nginx ----------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
