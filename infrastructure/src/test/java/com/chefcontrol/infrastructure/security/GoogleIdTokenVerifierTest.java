package com.chefcontrol.infrastructure.security;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lo que importa de este verificador no es la firma (eso es Nimbus) sino las reglas que se le suman: un
 * token bien firmado por Google pero para otra app, de otro emisor, vencido o sin email verificado no entra.
 */
class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "nuestro-client-id.apps.googleusercontent.com";
    private static final KeyPair KEYS = rsaKeys();

    private final GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(CLIENT_ID,
            NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build());

    @Test
    void acceptsValidTokenAndReturnsIdentity() {
        var identity = verifier.verify(token(c -> {}));
        assertEquals("google-sub-123", identity.subject());
        assertEquals("juan@gmail.com", identity.email());
        assertEquals("Juan García", identity.name());
    }

    @Test
    void acceptsIssuerWithoutScheme() {
        assertEquals("google-sub-123", verifier.verify(token(c -> c.issuer("accounts.google.com"))).subject());
    }

    @Test
    void fallsBackToEmailLocalPartWithoutName() {
        assertEquals("juan", verifier.verify(token(c -> c.claim("name", null))).name());
    }

    @Test
    void rejectsTokenForAnotherApp() {
        assertInvalid(token(c -> c.audience("otra-app.apps.googleusercontent.com")));
    }

    @Test
    void rejectsForeignIssuer() {
        assertInvalid(token(c -> c.issuer("https://evil.example.com")));
    }

    @Test
    void rejectsExpiredToken() {
        assertInvalid(token(c -> c.expirationTime(Date.from(Instant.now().minusSeconds(3600)))));
    }

    @Test
    void rejectsUnverifiedEmail() {
        assertInvalid(token(c -> c.claim("email_verified", false)));
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        assertInvalid(token(c -> {}, rsaKeys()));
    }

    @Test
    void rejectsEverythingWithoutClientId() {
        var unconfigured = new GoogleIdTokenVerifier("", NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build());
        var e = assertThrows(AppException.class, () -> unconfigured.verify(token(c -> {})));
        assertEquals(ErrorCode.GOOGLE_TOKEN_INVALID, e.getErrorCode());
    }

    private void assertInvalid(String idToken) {
        var e = assertThrows(AppException.class, () -> verifier.verify(idToken));
        assertEquals(ErrorCode.GOOGLE_TOKEN_INVALID, e.getErrorCode());
    }

    private static String token(Consumer<JWTClaimsSet.Builder> customize) {
        return token(customize, KEYS);
    }

    private static String token(Consumer<JWTClaimsSet.Builder> customize, KeyPair keys) {
        var claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("google-sub-123")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .claim("email", "juan@gmail.com")
                .claim("email_verified", true)
                .claim("name", "Juan García");
        customize.accept(claims);
        try {
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
            jwt.sign(new RSASSASigner(keys.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair rsaKeys() {
        try {
            var gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
