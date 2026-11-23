package com.chefcontrol.api.auth;

import com.chefcontrol.api.auth.dto.LoginRequest;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.service.PermissionResolutionService;
import com.chefcontrol.domain.repository.AccountRepository;
import com.chefcontrol.domain.repository.UserRepository;
import com.chefcontrol.domain.repository.UserRestaurantRepository;
import com.chefcontrol.domain.user.User;
import com.chefcontrol.domain.user.UserRestaurant;
import com.chefcontrol.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** iOS no guarda la cookie de sesión sin Partitioned (CHIPS); y una particionada solo se borra con otra igual. */
@ExtendWith(MockitoExtension.class)
class AuthControllerCookieTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock UserRepository userRepository;
    @Mock UserRestaurantRepository userRestaurantRepository;
    @Mock AccountRepository accountRepository;
    @Mock PermissionResolutionService permissionResolutionService;
    @Mock AuditService auditService;
    @InjectMocks AuthController controller;

    @Test
    void withTheFlagOnLoginAndLogoutCookiesArePartitioned() {
        partitioned(true);

        String login = loginCookie();
        assertTrue(login.startsWith("cc_session=jwt;"), login);
        assertTrue(login.contains("; Partitioned"), login);
        assertTrue(logoutCookie().contains("; Partitioned"), "logout must clear the partitioned cookie");
    }

    @Test
    void withTheFlagOffNeitherIsPartitioned() {
        partitioned(false);

        assertFalse(loginCookie().contains("Partitioned"));
        assertFalse(logoutCookie().contains("Partitioned"));
    }

    private void partitioned(boolean on) {
        ReflectionTestUtils.setField(controller, "cookieSecure", true);
        ReflectionTestUtils.setField(controller, "cookieSameSite", "None");
        ReflectionTestUtils.setField(controller, "cookiePartitioned", on);
        ReflectionTestUtils.setField(controller, "jwtExpirationMs", 60_000L);
    }

    private String loginCookie() {
        UUID restaurantId = UUID.randomUUID();
        User user = mock(User.class);
        UserRestaurant membership = mock(UserRestaurant.class);
        when(membership.getRestaurantId()).thenReturn(restaurantId);
        when(membership.getRoleName()).thenReturn("SUPERADMIN");
        when(userRepository.findByEmailAndIsActiveTrue("a@b.c")).thenReturn(Optional.of(user));
        when(userRestaurantRepository.findActiveByUserId(any())).thenReturn(List.of(membership));
        when(jwtTokenProvider.generateToken(eq(user), eq(restaurantId), anyList(), anyList())).thenReturn("jwt");

        var response = new MockHttpServletResponse();
        controller.login(new LoginRequest("a@b.c", "secret"), response);
        return response.getHeader(HttpHeaders.SET_COOKIE);
    }

    private String logoutCookie() {
        var response = new MockHttpServletResponse();
        controller.logout(response);
        return response.getHeader(HttpHeaders.SET_COOKIE);
    }
}
