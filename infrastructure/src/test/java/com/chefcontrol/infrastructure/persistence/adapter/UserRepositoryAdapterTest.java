package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.user.User;
import com.chefcontrol.infrastructure.persistence.jpa.JpaUserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** Invitar a "Juan@Gmail.com" cuando existía "juan@gmail.com" creaba un segundo usuario: el email no distinguía mayúsculas. */
class UserRepositoryAdapterTest {

    private final JpaUserRepository jpa = mock(JpaUserRepository.class);
    private final UserRepositoryAdapter adapter = new UserRepositoryAdapter(jpa);

    @Test
    void emailLookupsIgnoreCaseAndSurroundingSpaces() {
        when(jpa.findByEmail(any())).thenReturn(Optional.empty());
        when(jpa.findByEmailAndIsActiveTrue(any())).thenReturn(Optional.empty());

        adapter.findByEmail(" Juan@Gmail.com ");
        adapter.findByEmailAndIsActiveTrue("JUAN@gmail.com");
        adapter.existsByEmail("Juan@GMAIL.com\t");

        verify(jpa).findByEmail("juan@gmail.com");
        verify(jpa).findByEmailAndIsActiveTrue("juan@gmail.com");
        verify(jpa).existsByEmail("juan@gmail.com");
    }

    @Test
    void anEmailIsStoredNormalized() {
        User user = new User();
        user.setEmail("  Juan@Gmail.com ");
        assertEquals("juan@gmail.com", user.getEmail());
    }
}
