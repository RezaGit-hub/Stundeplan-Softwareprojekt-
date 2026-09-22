package de.wsp3_1.auth.api;

import tools.jackson.databind.ObjectMapper;
import de.wsp3_1.auth.domain.AuthService;
import de.wsp3_1.shared.api.ApiProblems;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Zugriffsschutz nach den Pfadregeln aus {@code api.md} (E-45, Stand 04.09.; Rolle {@code STUDENT}
 * ergaenzt 05.09., E-50):
 * <ul>
 *   <li>ohne Token: {@code GET /api/health}, {@code POST /api/auth/login}</li>
 *   <li>jedes angemeldete Konto: {@code DELETE /api/auth/token}, {@code GET /api/me},
 *       {@code GET /api/timegrid}</li>
 *   <li>jedes angemeldete Konto: {@code GET /api/me/class-week} — die Klasse kommt aus dem Token,
 *       ein Konto ohne Klasse (Planung, Lehrkraft) bekommt dort 404 statt Daten</li>
 *   <li>{@code PLANNING} und {@code TEACHER}: das uebrige {@code /api/me/**}
 *       (also {@code PUT /api/me/password}, {@code PUT /api/me/tour} und
 *       {@code GET /api/me/timetable/week})</li>
 *   <li>alles Uebrige unter {@code /api/**}: nur Rolle {@code PLANNING}</li>
 * </ul>
 * Ein Klassenkonto kommt damit an genau sechs Stellen vorbei und bekommt sonst ueberall 403 —
 * insbesondere auf {@code /api/timetable}, {@code /api/teachers} und {@code /api/me/timetable/week}.
 * Auch {@code PUT /api/me/password} ist fuer ein Klassenkonto gesperrt: das Passwort einer ganzen
 * Klasse gehoert der Schule und wird zentral gesetzt ({@code PUT /api/users/&#123;id&#125;/password}) —
 * sonst koennte eine einzelne Person die Klasse aussperren.
 * Wer einen Pfad neu baut, bekommt automatisch die Regel „nur Planung"; wer etwas fuer die Rolle
 * {@code TEACHER} oder {@code STUDENT} oeffnet, aendert diese Klasse <em>und</em>
 * {@code SecurityConfigTest} (VERTRAG Abschnitt 3, Regel 6).
 *
 * Sitzungslos, ohne Cookies, ohne CSRF-Token — der Client schickt bei jeder Anfrage sein Bearer-Token.
 * 401 und 403 kommen im selben ProblemDetail-Format wie alle anderen Fehler.
 *
 * <p>Das Team-Beispiel {@code /api/clients/**} aus dem Vite-Geruest ist seit dem 05.09. nicht mehr
 * freigegeben (VERTRAG, Phase backend1): der Code bleibt bis zur Liveschaltung liegen, die Daten
 * sind aber nur noch mit Anmeldung als Planung erreichbar.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, AuthService authService,
                                           ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Fehler- und Weiterleitungs-Zustellungen nicht erneut pruefen, sonst wird aus 500 ein 403.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD, DispatcherType.ASYNC)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/auth/token").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/timegrid").authenticated()
                        // Die einzige Fachsicht des Klassenkontos (E-50). Bewusst „angemeldet" und
                        // nicht „nur STUDENT": die Klasse kommt aus dem Token, ein Konto ohne Klasse
                        // bekommt 404 und nie fremde Daten. So bleibt der 404-Fall auch pruefbar.
                        .requestMatchers(HttpMethod.GET, "/api/me/class-week").authenticated()
                        // Muss NACH der Zeile darueber stehen: sonst faenge dieses Muster die
                        // Klassensicht ab und ein Klassenkonto bekaeme dort 403.
                        .requestMatchers("/api/absences/me", "/api/absences/me/**").hasRole("TEACHER")
                        .requestMatchers("/api/substitution-requests/me", "/api/substitution-requests/me/**").hasRole("TEACHER")
                        .requestMatchers("/api/me", "/api/me/**").hasAnyRole("PLANNING", "TEACHER")
                        .requestMatchers("/api/**").hasRole("PLANNING")
                        .anyRequest().denyAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthenticatedEntryPoint(objectMapper))
                        .accessDeniedHandler(forbiddenHandler(objectMapper)))
                .addFilterBefore(new TokenAuthenticationFilter(authService), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** Ohne diesen Einstiegspunkt antwortet Spring auf fehlende Anmeldung mit 403 statt 401 (api.md, Punkt 3). */
    private AuthenticationEntryPoint unauthenticatedEntryPoint(ObjectMapper objectMapper) {
        return (request, response, exception) -> writeProblem(response, objectMapper, ApiProblems.unauthenticated());
    }

    private AccessDeniedHandler forbiddenHandler(ObjectMapper objectMapper) {
        return (request, response, exception) -> writeProblem(response, objectMapper, ApiProblems.forbidden());
    }

    private void writeProblem(HttpServletResponse response, ObjectMapper objectMapper, ProblemDetail problem)
            throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    /** BCrypt mit Staerke 10 (Standard) — so steht es im Datenmodell. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
