# Frontend: build the Vite bundle, serve it via nginx which reverse-proxies the
# API so the browser sees a single origin (no CORS, no VITE_API_BASE needed).
FROM node:22-alpine AS build
WORKDIR /web
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /web/dist /usr/share/nginx/html
EXPOSE 80
