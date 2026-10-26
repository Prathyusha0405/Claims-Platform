# syntax=docker/dockerfile:1
#
# The Angular app, built once and served by nginx, which also forwards /api/* to the three services (see nginx.conf).
#
#   docker build -t chubb-claims/frontend frontend/claims-portal

# ---- build: the production bundle ---------------------------------------------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

# ---- run: static files + reverse proxy ----------------------------------------------------------------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/claims-portal/browser /usr/share/nginx/html
EXPOSE 80
