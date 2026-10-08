# Cliente web (Angular) servido por nginx, que además reenvía /api al backend:
# el navegador ve un solo origen, así que no hace falta CORS.

FROM node:22-alpine AS compilacion
WORKDIR /fuente
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=compilacion /fuente/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
