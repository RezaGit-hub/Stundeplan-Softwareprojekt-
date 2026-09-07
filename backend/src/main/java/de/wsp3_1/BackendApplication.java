package de.wsp3_1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling schaltet die @Scheduled-Methoden ein (Modul auth: TokenCleanup loescht taeglich
// abgelaufene Sitzungen). Additiv ergaenzt am 05.09.; gilt fuer alle Module.
//
// exclude = UserDetailsServiceAutoConfiguration: Ohne diese Zeile legt Spring Boot beim Start ein
// Konto "user" mit einem Zufallspasswort an und schreibt es ins Log
// ("Using generated security password: <uuid>"). Das Konto ist hier wirkungslos — Basic-Auth und
// Formular-Anmeldung sind in SecurityConfig abgeschaltet, angemeldet wird ausschliesslich ueber
// POST /api/auth/login und den TokenAuthenticationFilter. Ein Passwort im Log widerspricht aber der
// Zusage "wird nie protokolliert" (Befund 05.09.), deshalb ist die Auto-Konfiguration aus.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
