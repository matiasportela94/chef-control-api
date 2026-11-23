package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.EmailService;
import com.chefcontrol.application.port.PasswordEncoderPort;
import com.chefcontrol.application.port.WhatsAppService;
import com.chefcontrol.domain.repository.PasswordResetTokenRepository;
import com.chefcontrol.domain.repository.UserRepository;
import com.chefcontrol.domain.token.PasswordResetToken;
import com.chefcontrol.domain.token.TokenType;
import com.chefcontrol.domain.user.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final PasswordEncoderPort encoder = mock(PasswordEncoderPort.class);
    private final EmailService email = mock(EmailService.class);
    private final PasswordResetService service = new PasswordResetService(users, tokens, encoder, email,
            mock(WhatsAppService.class), mock(AuditService.class));

    private User ana() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail("ana@example.com");
        u.setName("Ana");
        return u;
    }

    @Test
    void theDatabaseGetsTheHashAndOnlyTheLinkCarriesTheToken() {
        User ana = ana();
        when(users.findByEmailAndIsActiveTrue(any())).thenReturn(Optional.of(ana));

        service.requestPasswordReset("ana@example.com");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(email).sendPasswordResetLink(eq("ana@example.com"), eq("Ana"), link.capture());
        UUID token = UUID.fromString(link.getValue().substring(link.getValue().indexOf("token=") + 6));
        assertEquals(PasswordResetToken.hash(token), saved.getValue().getTokenHash());
        assertEquals(64, saved.getValue().getTokenHash().length());
        assertFalse(saved.getValue().getTokenHash().contains(token.toString()));

        // El link vuelve con el token y se busca por su hash.
        PasswordResetToken stored = saved.getValue();
        stored.setType(TokenType.PASSWORD_RESET);
        stored.setExpiresAt(Instant.now().plusSeconds(600));
        when(tokens.findByTokenHash(PasswordResetToken.hash(token))).thenReturn(Optional.of(stored));
        when(users.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(encoder.encode("Nueva-1234")).thenReturn("hash");

        service.resetPassword(token, "Nueva-1234");

        assertEquals("hash", ana.getPasswordHash());
        assertTrue(stored.isUsed());
    }

    @Test
    void aSecondRequestWithinAMinuteForTheSameEmailSendsNothing() {
        when(users.findByEmailAndIsActiveTrue(any())).thenReturn(Optional.of(ana()));

        service.requestPasswordReset("ana@example.com");
        service.requestPasswordReset("  ANA@Example.com ");

        verify(tokens, times(1)).save(any());
        verify(email, times(1)).sendPasswordResetLink(any(), any(), any());
    }

    @Test
    void oneEveryMinuteFiveAnHourAndOldEntriesAreDropped() {
        var limiter = new PasswordResetService.ResetLimiter();
        Instant t = Instant.parse("2026-11-23T12:00:00Z");

        assertTrue(limiter.allow("ana", t));
        assertFalse(limiter.allow("ana", t.plusSeconds(59)), "otro dentro del minuto");
        assertTrue(limiter.allow("beto", t.plusSeconds(59)), "otro email cuenta aparte");
        for (int i = 1; i <= 4; i++) assertTrue(limiter.allow("ana", t.plus(Duration.ofMinutes(i))));
        assertFalse(limiter.allow("ana", t.plus(Duration.ofMinutes(30))), "el sexto en la hora");
        assertTrue(limiter.allow("ana", t.plus(Duration.ofMinutes(61))), "el primero ya salió de la hora");

        limiter.allow("carla", t.plus(Duration.ofHours(3)));
        assertEquals(1, limiter.trackedKeys(), "lo de hace más de una hora se borra");
    }
}
