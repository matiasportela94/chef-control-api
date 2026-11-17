package com.chefcontrol.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationRequiredEntryPointTest {

    @Test
    void withoutASessionTheAnswerIs401WithTheUsualErrorBody() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var response = new MockHttpServletResponse();

        new AuthenticationRequiredEntryPoint(mapper).commence(new MockHttpServletRequest("GET", "/products"), response,
                new InsufficientAuthenticationException("no session"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        var body = mapper.readTree(response.getContentAsString());
        assertEquals("AUTHENTICATION_REQUIRED", body.get("errorCode").asText());
        assertEquals("/products", body.get("path").asText());
        assertNotNull(body.get("timestamp"));
    }
}
