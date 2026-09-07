package de.wsp3_1.auth.api;

import de.wsp3_1.auth.domain.AuthService;
import de.wsp3_1.shared.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Liest {@code Authorization: Bearer <token>} und setzt daraus die angemeldete Person.
 * Fehlt oder taugt das Token nichts, laeuft die Kette ohne Authentication weiter — den 401 schreibt
 * dann der Einstiegspunkt in {@link SecurityConfig}.
 *
 * <p>Als Principal liegt {@link CurrentUser} aus {@code shared} in der Kette: so lesen {@code planning},
 * {@code substitution} und {@code changelog} den Verursacher einer Aenderung, ohne {@code auth} zu
 * importieren (VERTRAG Abschnitt 4).</p>
 *
 * <p>Bewusst <b>kein</b> {@code @Component}: ein Filter-Bean haengt Spring Boot zusaetzlich in die
 * allgemeine Servlet-Kette und in jeden {@code @WebMvcTest} jedes anderen Moduls — dort fehlt dann
 * der {@code AuthService}. Erzeugt wird der Filter deshalb in {@link SecurityConfig}, genau einmal
 * und nur fuer die Sicherheitskette.</p>
 *
 * <p>Der Filter liegt in {@code auth.api} und nicht in {@code auth.domain} (E-45 zielt auf das Modul):
 * er erbt von {@code org.springframework.web.filter.OncePerRequestFilter}, was ArchUnit-Regel 3 in
 * {@code domain} verbietet.</p>
 */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final AuthService authService;

    public TokenAuthenticationFilter(AuthService authService) {
        this.authService = authService;
    }

    /** Holt das Klartext-Token aus dem Header; {@code null}, wenn keins mitgeschickt wurde. */
    static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            return null;
        }
        String token = header.substring(BEARER.length()).trim();
        return token.isEmpty() ? null : token;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = bearerToken(request);
            if (token != null) {
                authService.authenticate(token).ifPresent(this::setAuthentication);
            }
        }
        chain.doFilter(request, response);
    }

    private void setAuthentication(CurrentUser user) {
        var authorities = List.of(new SimpleGrantedAuthority(user.role().authority()));
        var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
