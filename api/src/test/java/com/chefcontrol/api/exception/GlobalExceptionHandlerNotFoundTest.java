package com.chefcontrol.api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Una ruta borrada o un método equivocado es 404 NOT_FOUND, no el 500 del handler genérico. */
class GlobalExceptionHandlerNotFoundTest {

    @RestController
    static class Probe {
        @PostMapping("/only-post")
        void onlyPost() {}

        /** Lo que tira el handler de recursos estáticos de Spring con una ruta que ningún controller atiende. */
        @GetMapping("/gone")
        void gone() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.POST, "ai/interpret");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void aPathNoControllerServesIsNotFound() throws Exception {
        mvc.perform(get("/gone"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/gone"));
    }

    @Test
    void aMethodThePathDoesNotAcceptIsNotFound() throws Exception {
        mvc.perform(get("/only-post"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        mvc.perform(post("/only-post")).andExpect(status().isOk());
    }
}
