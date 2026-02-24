package org.openphc.cce.compliance.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class TestErrorController {

        @GetMapping("/test/not-found")
        public void notFound() {
            throw new NoSuchElementException("Resource not found");
        }

        @GetMapping("/test/bad-request")
        public void badRequest() {
            throw new IllegalArgumentException("Invalid parameter");
        }

        @GetMapping("/test/conflict")
        public void conflict() {
            throw new IllegalStateException("Resource already exists");
        }

        @GetMapping("/test/internal-error")
        public void internalError() {
            throw new RuntimeException("Unexpected error");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestErrorController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("should return 404 for NoSuchElementException")
    void shouldReturn404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Resource not found"));
    }

    @Test
    @DisplayName("should return 400 for IllegalArgumentException")
    void shouldReturn400() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid parameter"));
    }

    @Test
    @DisplayName("should return 409 for IllegalStateException")
    void shouldReturn409() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Resource already exists"));
    }

    @Test
    @DisplayName("should return 500 for unhandled exceptions")
    void shouldReturn500() throws Exception {
        mockMvc.perform(get("/test/internal-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }
}
