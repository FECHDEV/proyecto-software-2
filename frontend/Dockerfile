# Cliente web (Angular) servido por nginx, que además reenvía /api al backend:
# el navegador ve un solo origen, así que no hace falta CORS.

FROM node:26.12-alpine AS compilacion
WORKDIR /fuente
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production
# La política de contenido, con el hash de los scripts en línea del index.html recién compilado
RUN node scripts/politica-de-contenido.mjs dist/frontend/browser/index.html > dist/politica-de-contenido.conf

FROM nginx:1.30-alpine
# Como plantilla: al arrancar, la imagen de nginx reemplaza ${TIENDA_SITIO_URL}
# (solo las variables TIENDA_, para no tocar las de nginx como $uri)
ENV NGINX_ENVSUBST_FILTER=^TIENDA_
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=compilacion /fuente/dist/politica-de-contenido.conf /etc/nginx/politica-de-contenido.conf
COPY --from=compilacion /fuente/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
