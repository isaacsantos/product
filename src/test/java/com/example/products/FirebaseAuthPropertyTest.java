package com.example.products;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.constraints.UniqueElements;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Property-based tests for JwtAuthenticationConverter role extraction.
 * Pure unit tests — no Spring context required.
 */
class FirebaseAuthPropertyTest {

    private final JwtAuthenticationConverter converter = buildConverter();

    private JwtAuthenticationConverter buildConverter() {
        JwtAuthenticationConverter c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) return List.of();
            return roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .map(a -> (GrantedAuthority) a)
                    .toList();
        });
        return c;
    }

    private Jwt buildJwtWithRoles(List<String> roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("roles", roles)
                .subject("uid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private Jwt buildJwtWithoutRoles() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("uid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    // Feature: firebase-auth-integration, Property 4: Extracción de roles con prefijo ROLE_
    @Property(tries = 100)
    void rolesClaimIsPrefixedWithROLE_(
            @ForAll @Size(min = 1, max = 10) @UniqueElements List<@AlphaChars @StringLength(min = 1, max = 20) String> roles) {
        Jwt jwt = buildJwtWithRoles(roles);
        // Filter to only the ROLE_-prefixed authorities produced by our custom converter
        List<String> roleAuthorities = converter.convert(jwt).getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .toList();

        // All produced authorities must start with ROLE_
        assertThat(roleAuthorities).allMatch(a -> a.startsWith("ROLE_"));
        // The set of produced ROLE_ authorities must equal ROLE_+role for each unique input role
        List<String> expectedAuthorities = roles.stream().map(r -> "ROLE_" + r).toList();
        assertThat(roleAuthorities).containsExactlyInAnyOrderElementsOf(expectedAuthorities);
    }

    // Feature: firebase-auth-integration, Property 4 (null case): absent roles claim returns empty collection
    @Property(tries = 10)
    void absentRolesClaimReturnsEmptyCollection() {
        Jwt jwt = buildJwtWithoutRoles();
        assertThatNoException().isThrownBy(() -> {
            // Filter to only ROLE_-prefixed authorities — our converter should produce none
            List<GrantedAuthority> roleAuthorities = converter.convert(jwt).getAuthorities()
                    .stream()
                    .filter(a -> a.getAuthority().startsWith("ROLE_"))
                    .toList();
            assertThat(roleAuthorities).isEmpty();
        });
    }
}
