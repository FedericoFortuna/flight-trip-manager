package testsupport;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Explicit test-only import, outside application component scanning. Never packaged in the JAR. */
@TestConfiguration(proxyBeanMethods = false)
public class ErrorProbeConfiguration {
    @RestController
    @RequestMapping("/api/v1/probe")
    public static class ProbeController {
        @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
        public String validate(@Valid @RequestBody ProbeRequest body) {
            return "ok";
        }

        @GetMapping("/lookup")
        public String lookup(@RequestParam LocalDate date) {
            return "ok";
        }

        @GetMapping("/count")
        public String count(@RequestParam @Min(1) int count) {
            return "ok";
        }

        @GetMapping("/{id}")
        public String byId(@PathVariable UUID id) {
            return "ok";
        }

        @GetMapping("/failure")
        public String failure() {
            throw new IllegalStateException("pnr=ABC123 token=private-token");
        }

        @GetMapping("/dispatch")
        public void dispatch(HttpServletResponse response) throws IOException {
            response.sendError(400, "pnr=ABC123 token=private-token");
        }

        @GetMapping(value = "/representation/json", produces = MediaType.APPLICATION_JSON_VALUE)
        public ProbeRequest json() {
            return new ProbeRequest("ok");
        }
    }

    public record ProbeRequest(@NotBlank(message = "pnr=ABC123 token=private-token") String name) { }
}
