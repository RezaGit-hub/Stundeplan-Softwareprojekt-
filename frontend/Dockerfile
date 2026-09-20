# Web-Container: Frontend bauen, dann von nginx ausliefern.
#
# Der Build-Kontext ist die REPO-WURZEL (nicht frontend/), weil die zweite Stufe
# deployment/nginx.conf, deployment/nginx-common.conf und deployment/security-headers.conf
# braucht. docker-compose.yml setzt das so:
#   build: { context: .., dockerfile: frontend/Dockerfile }
# Von Hand:
#   docker build -f frontend/Dockerfile -t stundenplan-web .

# ---------------------------------------------------------------------------
# Stufe 1: Frontend bauen
# ---------------------------------------------------------------------------

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

# ---------------------------------------------------------------------------
# Stufe 2: Ausliefern mit nginx (HTTP und HTTPS)
# ---------------------------------------------------------------------------

# Feste Marke statt `nginx:alpine`: der wandernde Tag lieferte am 05.09. bereits die
# Mainline-Version 1.31.5, obwohl der Betrieb eine stabile Linie erwartet (Befund BETRIEB-9).
FROM nginx:1.31-alpine

# Selbstsigniertes Zertifikat direkt im Image erzeugen.
# Kein externer Dienst, kein Volume noetig — das Zertifikat lebt und stirbt mit dem Image.
# CN ist die VM-IP (keine oeffentliche Domain vorhanden). SSLCN kommt als Build-Arg,
# damit die VM-Gruppe ihre eigene IP mitgeben kann (siehe docker-compose.https.yml, build.args).
ARG SSLCN=134.245.1.240
RUN apk add --no-cache openssl \
 && mkdir -p /etc/nginx/ssl \
 && openssl req -x509 -nodes -days 825 -newkey rsa:2048 \
    -keyout /etc/nginx/ssl/selfsigned.key \
    -out /etc/nginx/ssl/selfsigned.crt \
    -subj "/CN=$(SSLCN)"

# Statische Dateien aus der Build-Stufe.
COPY --from=build /w/dist /usr/share/nginx/html

# nginx-Konfiguration. Drei Dateien, alle aus deployment/ (Build-Kontext ist die Repo-Wurzel):
#   - nginx.conf             Server-Bloecke (HTTP auf 8080, HTTPS auf 8443)
#   - nginx-common.conf      gemeinsame Locations, von beiden Server-Bloecken included
#   - security-headers.conf  CSP und uebrige Security-Header
COPY deployment/nginx.conf /etc/nginx/conf.d/default.conf
COPY deployment/nginx-common.conf /etc/nginx/nginx-common.conf
COPY deployment/security-headers.conf /etc/nginx/security-headers.conf

# 8080 = HTTP (immer erreichbar, wenn Port veroeffentlicht)
# 8443 = HTTPS (nur erreichbar, wenn docker-compose.https.yml den Port veroeffentlicht)
# Beide Ports sind nur informativ; das tatsaechliche Port-Mapping macht docker-compose.
EXPOSE 8080 8443