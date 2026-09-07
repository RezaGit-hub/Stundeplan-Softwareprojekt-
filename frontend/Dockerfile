# Web-Container: Frontend bauen, dann von nginx ausliefern.
#
# Der Build-Kontext ist die REPO-WURZEL (nicht frontend/), weil die zweite Stufe
# deployment/nginx.conf braucht. docker-compose.yml setzt das so:
#   build: { context: .., dockerfile: frontend/Dockerfile }
# Von Hand:
#   docker build -f frontend/Dockerfile -t stundenplan-web .

# Feste Marke statt `node:22-alpine` (Befund BETRIEB-9, 05.09.): ein wandernder Tag baut morgen
# etwas anderes als heute — gemessen v22.23.2 am 05.09.
FROM node:22.23-alpine AS build
WORKDIR /w

# Erst die Paketlisten: solange sie sich nicht aendern, bleibt npm ci im Cache.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
# Baut nach /w/dist. Keine Umgebungsvariable noetig: die API-Basis ist immer der relative Pfad /api.
RUN npm run build

# Feste Marke statt `nginx:alpine`: der wandernde Tag lieferte am 05.09. bereits die
# Mainline-Version 1.31.5, obwohl der Betrieb eine stabile Linie erwartet (Befund BETRIEB-9).
FROM nginx:1.31-alpine
COPY --from=build /w/dist /usr/share/nginx/html
COPY deployment/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
