package com.belife.partial_maturity_backend.security;

import com.belife.partial_maturity_backend.entities.AppUserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;

/**
 * Génère et vérifie les JWT utilisés par l'API.
 *
 * <p>Le secret fourni par JWT_SECRET doit être encodé en Base64
 * et suffisamment long pour produire une clé HMAC sécurisée.</p>
 */
@Service
public class JwtServiceImpl implements JwtService {

    private final String jwtSecret;

    private final long expirationMillis;

    public JwtServiceImpl(
            @Value("${application.jwt.secret}")String jwtSecret,
            @Value("${application.jwt.expiration-ms}") long expirationMillis) {
        this.jwtSecret = jwtSecret;
        this.expirationMillis = expirationMillis;
    }

    /**
     * Génère un JWT contenant uniquement les informations utiles
     * à l'identification et à l'autorisation.
     *
     * @param user utilisateur authentifié
     * @return token signé
     */
    @Override
    public String generateToken(AppUserEntity user) {
        Instant now = Instant.now();
        Instant expiration = now.plusMillis(expirationMillis);

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(getSigningKey())
                .compact();
    }

    @Override
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    @Override
    public boolean isTokenValid(
            String token,
            UserDetails userDetails
    ) {
        String username = extractUsername(token);

        return username.equalsIgnoreCase(userDetails.getUsername())
                && !isTokenExpired(token)
                && userDetails.isEnabled();
    }

    @Override
    public long getExpirationMillis() {
        return expirationMillis;
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractClaim(token, Claims::getExpiration);
        return expiration.before(new Date());
    }

    private <T> T extractClaim(
            String token,
            Function<Claims, T> claimResolver
    ) {
        Claims claims = extractAllClaims(token);
        return claimResolver.apply(claims);
    }

    /**
     * Vérifie la signature avant de retourner les données du token.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
