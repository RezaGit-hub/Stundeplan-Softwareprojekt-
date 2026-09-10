# Zwei Stufen: bauen mit dem vollen JDK, ausliefern nur mit der Laufzeitumgebung.
# Das fertige Abbild braucht damit weder Gradle noch Quelltext (rund 200 MB statt ueber einem Gigabyte).
# Kontext ist backend/ — gebaut wird mit: docker build -t stundenplan-backend backend/

FROM gradle:9.7.1-jdk21 AS build
WORKDIR /home/gradle/project
# Erst die Bauskripte, dann die Abhaengigkeiten, dann der Quelltext (Befund BETRIEB-3, 05.09.):
# Docker legt fuer jede COPY-Zeile eine eigene Schicht an und baut ab der ersten geaenderten neu.
# Lag der ganze Ordner in einer Schicht, lud jede Quelltextaenderung alle Abhaengigkeiten erneut —
# auf der 4-GB-VM der langsamste Schritt des Deploys.
COPY --chown=gradle:gradle build.gradle settings.gradle ./
COPY --chown=gradle:gradle gradle ./gradle
# `-x test` reicht nicht: `dependencies` laedt genau die Bibliotheken, die bootJar danach braucht.
# Schlaegt der Schritt fehl (kein Netz), soll der Bau trotzdem weiterlaufen — bootJar holt sie dann.
RUN gradle --no-daemon dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true
COPY --chown=gradle:gradle src ./src
# Tests laufen in der Pipeline, nicht im Abbild-Bau: sie brauchen eine Datenbank.
RUN gradle bootJar -x test --no-daemon

# Feste Marke statt `eclipse-temurin:21-jre` (Befund BETRIEB-9, 05.09.): sonst wechselt die
# Laufzeit unbemerkt mit dem naechsten Build.
FROM eclipse-temurin:21.0.12_8-jre
WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/app.jar /app/app.jar
EXPOSE 8080
# Zeitzone der Anwendung. Ohne sie laeuft der Container in UTC, und alles Datierte (Verlaufseintrag,
# verfallende Vertretungen, laufende Woche) liegt zwischen Mitternacht und 2 Uhr einen Tag daneben.
# deployment/docker-compose.yml setzt denselben Wert noch einmal und kann ihn ohne Neubau aendern.
ENV TZ=Europe/Berlin
# Speichergrenze setzt der Betrieb ueber JAVA_TOOL_OPTIONS in deployment/docker-compose.yml.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
