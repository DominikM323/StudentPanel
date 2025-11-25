package com.example.sw;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;  // Twoja klasa od tokenów
    private final UserDetailsService userDetailsService; // pobiera użytkownika z DB
    private final DBEngine databaseService;

    public JwtAuthFilter(JwtService jwtService, UserDetailsService userDetailsService,DBEngine databaseService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.databaseService = databaseService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Brak nagłówka -> przepuszczamy dalej, nie próbujemy autoryzować
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);  // ucinamy "Bearer "
        final String username;

        try {
            username = jwtService.extractUsername(token);

        } catch (Exception e) {
            // Token niepoprawny -> odrzucamy request
            System.out.println("Invalid JWT: " + e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        // Jeśli mamy username i brak existing Authentication (bardzo ważne!)
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
            CustomUserDetails userDetails = (CustomUserDetails) databaseService.loadUserByUsername(username);
            System.out.println("username: " + userDetails.getUsername());
            if (jwtService.isTokenValid(token, userDetails)) {

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Ustawiamy autoryzację na cały request
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
            } catch (UsernameNotFoundException e) {
                // Użytkownik nie istnieje → zakończ filtrowanie, zwróć 401
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        // Lecimy dalej
        filterChain.doFilter(request, response);
    }
}