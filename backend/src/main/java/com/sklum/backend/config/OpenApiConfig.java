package com.sklum.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Live docs at /swagger-ui/index.html, raw spec at /v3/api-docs — see also /prototype/openapi.yaml
 *  (hand-written, for reading or importing into Postman without running the app). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sklumBackendOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SKLUM Backend — prototype")
                .version("0.1.0")
                .description("""
                        Domain deep-dive prototype for the SKLUM App case study: search (Postgres trigram), \
                        delivery-promise calculation (two-tier precompute + live inventory check), order \
                        orchestration (saga with compensation), and operational observability. \
                        See SKLUM_System_Architecture (Fig. 2/4/5) for the design this implements.""")
                .contact(new Contact().name("SKLUM App — Software Engineering Case Study")));
    }
}
