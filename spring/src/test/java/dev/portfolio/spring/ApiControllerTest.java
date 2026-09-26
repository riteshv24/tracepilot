package dev.portfolio.spring;

import dev.portfolio.foundation.*;
import dev.portfolio.tracepilot.Product;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.Path;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual Spring MVC adapter contract. Run through Maven; the dependency-free suite tests the same engine separately. */
class ApiControllerTest {
    private Application app;
    private MockMvc mvc;
    @BeforeEach void setup() {
        app=new Product(new MemoryStore(), new ModelClient("http://localhost:11434", "", ""));
        Config config=new Config(8311, "127.0.0.1", "test-workspace-key", "", Path.of("unused"), Path.of("web"), "", "", "", "http://localhost:11434", "", "");
        mvc=MockMvcBuilders.standaloneSetup(new ApiController(new Gateway(app, config))).build();
    }
    @AfterEach void close() {
        app.close();
    }
    @Test void healthIsPublicButHostChecked() throws Exception {
        mvc.perform(get("/health").header("Host", "localhost:8311")) .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP")) .andExpect(header().exists("Content-Security-Policy"));
    }
    @Test void missingKeyReturnsAProblemResponse() throws Exception {
        mvc.perform(get("/api/meta").header("Host", "localhost:8311")) .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.title").value("unauthorized"));
    }
    @Test void configuredKeyAllowsMetadataWithoutExposingSecrets() throws Exception {
        mvc.perform(get("/api/meta").header("Host", "localhost:8311").header("X-Workspace-Key", "test-workspace-key")) .andExpect(status().isOk()).andExpect(jsonPath("$.storage").value("memory")) .andExpect(jsonPath("$.token").doesNotExist());
    }
    @Test void foreignOriginIsRejected() throws Exception {
        mvc.perform(post("/api/unknown").header("Host", "localhost:8311").header("X-Workspace-Key", "test-workspace-key") .header("Origin", "https://untrusted.example").contentType("application/json").content("{}")) .andExpect(status().isForbidden());
    }
    @Test void oversizedBodyIsRejectedBeforeParsing() throws Exception {
        mvc.perform(post("/api/unknown").header("Host", "localhost:8311").header("X-Workspace-Key", "test-workspace-key") .contentType("application/json").content("x".repeat(Gateway.MAX_BODY+1))) .andExpect(status().isPayloadTooLarge());
    }
    @Test void unmappedApiPathUsesSafeNotFoundShape() throws Exception {
        mvc.perform(get("/api/unknown").header("Host", "localhost:8311").header("X-Workspace-Key", "test-workspace-key")) .andExpect(status().isNotFound()).andExpect(jsonPath("$.requestId").isString());
    }
}
