package com.sklum.backend.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@Tag(name = "Ops", description = "Search latency, promise cache, saga outcomes, circuit breaker states")
public class DashboardController {

    /**
     * The actual page is a static file (src/main/resources/static/dashboard.html); this just
     * gives it a clean URL. An explicit redirect rather than a "forward:" view-name string —
     * this app has no ViewResolver configured (it's REST-only), so a "forward:" return value
     * under @RestController is written out as literal response text instead of being resolved
     * as a view (caught this running it: GET /dashboard returned the string "forward:/dashboard.html").
     */
    @Operation(summary = "Static ops dashboard (HTML) — polls /ops/summary")
    @GetMapping("/dashboard")
    public void dashboard(HttpServletResponse response) throws IOException {
        response.sendRedirect("/dashboard.html");
    }
}
