package com.kayogx.eventcard.auth;

import com.kayogx.eventcard.user.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates and reads login tokens (JWT).
 *
 * A token is like a signed visitor badge: it says "this is user X of company Y",
 * and our secret signature proves we issued it and nobody changed it.
 */
@Component
public class JwtService {

    private final SecretKey signingKey;
    private final Duration tokenLifetime;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.valid-for-hours}") long validForHours) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenLifetime = Duration.ofHours(validForHours);
    }

    public String createToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("companyId", user.getCompanyId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(tokenLifetime)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Returns the user id inside the token,
     * or nothing if the token is fake, damaged or expired.
     */
    public Optional<UUID> readUserId(String token) {
        try {
            String userId = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.of(UUID.fromString(userId));
        } catch (JwtException | IllegalArgumentException invalidToken) {
            return Optional.empty();
        }
    }
}
