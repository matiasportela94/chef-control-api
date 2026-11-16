package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.GoogleIdentityVerifier;
import com.chefcontrol.application.port.GoogleIdentityVerifier.GoogleIdentity;
import com.chefcontrol.application.port.PasswordEncoderPort;
import com.chefcontrol.application.service.RestaurantRegistrationService.RegisterCommand;
import com.chefcontrol.application.service.RestaurantRegistrationService.RegisteredRestaurant;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.repository.UserRepository;
import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleSignInServiceTest {

    @Mock GoogleIdentityVerifier verifier;
    @Mock UserRepository userRepository;
    @Mock RestaurantRegistrationService registrationService;
    @Mock AuditService auditService;
    @Mock PasswordEncoderPort passwordEncoder;

    private static final GoogleIdentity GOOGLE = new GoogleIdentity("sub-1", "juan@gmail.com", "Juan");

    private GoogleSignInService service;

    @BeforeEach
    void setUp() {
        service = new GoogleSignInService(verifier, userRepository, registrationService, auditService, passwordEncoder);
        when(verifier.verify("token")).thenReturn(GOOGLE);
    }

    @Test
    void knownGoogleUserLogsIn() {
        User user = user("sub-1");
        when(userRepository.findByGoogleSubject("sub-1")).thenReturn(Optional.of(user));

        var result = service.signIn("token", null, null);

        assertSame(user, result.user());
        assertNull(result.registered());
        verify(userRepository, never()).save(any());
    }

    @Test
    void existingPasswordUserIsLinkedByVerifiedEmail() {
        User user = user(null);
        when(userRepository.findByGoogleSubject("sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("juan@gmail.com")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        var result = service.signIn("token", null, null);

        assertEquals("sub-1", result.user().getGoogleSubject());
        verify(auditService).log(eq(AuditAction.USER_GOOGLE_LINKED), eq("User"), eq(user.getId()), anyMap());
    }

    @Test
    void emailAlreadyLinkedToAnotherGoogleAccountIsRejected() {
        when(userRepository.findByGoogleSubject("sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("juan@gmail.com")).thenReturn(Optional.of(user("otro-sub")));

        var e = assertThrows(AppException.class, () -> service.signIn("token", "La Parrilla", null));
        assertEquals(ErrorCode.DUPLICATE_EMAIL, e.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void inactiveUserCannotSignIn() {
        User user = user("sub-1");
        user.setActive(false);
        when(userRepository.findByGoogleSubject("sub-1")).thenReturn(Optional.of(user));

        var e = assertThrows(AppException.class, () -> service.signIn("token", null, null));
        assertEquals(ErrorCode.USER_INACTIVE, e.getErrorCode());
    }

    @Test
    void newUserWithoutRestaurantNameIsAskedForIt() {
        when(userRepository.findByGoogleSubject("sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("juan@gmail.com")).thenReturn(Optional.empty());

        var e = assertThrows(AppException.class, () -> service.signIn("token", "  ", null));
        assertEquals(ErrorCode.GOOGLE_ACCOUNT_NOT_REGISTERED, e.getErrorCode());
        verifyNoInteractions(registrationService);
    }

    @Test
    void newUserWithRestaurantNameRegistersWithoutPassword() {
        when(userRepository.findByGoogleSubject("sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("juan@gmail.com")).thenReturn(Optional.empty());
        User owner = user("sub-1");
        var registered = new RegisteredRestaurant(owner, new Restaurant(), List.of());
        when(registrationService.register(any())).thenReturn(registered);

        var result = service.signIn("token", " La Parrilla ", "America/Argentina/Cordoba");

        var cmd = ArgumentCaptor.forClass(RegisterCommand.class);
        verify(registrationService).register(cmd.capture());
        assertEquals("La Parrilla", cmd.getValue().restaurantName());
        assertEquals("juan@gmail.com", cmd.getValue().ownerEmail());
        assertNull(cmd.getValue().ownerPassword());
        assertEquals("sub-1", cmd.getValue().googleSubject());
        assertSame(registered, result.registered());
    }

    // ── Vincular / desvincular desde el perfil ─────────────────────────────

    @Test
    void linkSetsSubjectEvenWithADifferentEmail() {
        User user = user(null);
        user.setEmail("juan@restaurante.com");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.findByGoogleSubject("sub-1")).thenReturn(Optional.empty());

        service.linkGoogleAccount(user.getId(), "token");

        assertEquals("sub-1", user.getGoogleSubject());
        verify(userRepository).save(user);
        verify(auditService).log(eq(AuditAction.USER_GOOGLE_LINKED), eq("User"), eq(user.getId()), anyMap());
    }

    @Test
    void linkingTheSameGoogleAccountAgainDoesNothing() {
        User user = user("sub-1");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service.linkGoogleAccount(user.getId(), "token");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void linkIsRejectedWhenTheUserAlreadyHasAnotherGoogleAccount() {
        User user = user("otro-sub");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        var e = assertThrows(AppException.class, () -> service.linkGoogleAccount(user.getId(), "token"));
        assertEquals(ErrorCode.USER_ALREADY_LINKED_TO_GOOGLE, e.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void linkIsRejectedWhenTheGoogleAccountBelongsToAnotherUser() {
        User user = user(null);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.findByGoogleSubject("sub-1")).thenReturn(Optional.of(user("sub-1")));

        var e = assertThrows(AppException.class, () -> service.linkGoogleAccount(user.getId(), "token"));
        assertEquals(ErrorCode.GOOGLE_ACCOUNT_ALREADY_LINKED, e.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void unlinkReplacesThePasswordAndClearsTheSubject() {
        User user = user("sub-1");
        user.setPasswordHash("hash-viejo");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("nueva-clave")).thenReturn("hash-nuevo");

        service.unlinkGoogleAccount(user.getId(), "token", "nueva-clave");

        assertNull(user.getGoogleSubject());
        assertEquals("hash-nuevo", user.getPasswordHash());
        verify(userRepository).save(user);
        verify(auditService).log(eq(AuditAction.USER_GOOGLE_UNLINKED), eq("User"), eq(user.getId()), anyMap());
    }

    @Test
    void unlinkNeedsATokenFromTheLinkedGoogleAccount() {
        User linkedElsewhere = user("otro-sub");
        User notLinked = user(null);
        when(userRepository.findById(linkedElsewhere.getId())).thenReturn(Optional.of(linkedElsewhere));
        when(userRepository.findById(notLinked.getId())).thenReturn(Optional.of(notLinked));

        for (User u : List.of(linkedElsewhere, notLinked)) {
            var e = assertThrows(AppException.class, () -> service.unlinkGoogleAccount(u.getId(), "token", "nueva-clave"));
            assertEquals(ErrorCode.GOOGLE_ACCOUNT_MISMATCH, e.getErrorCode());
        }
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    private static User user(String googleSubject) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail("juan@gmail.com");
        u.setGoogleSubject(googleSubject);
        return u;
    }
}
