package sn.dakarterminal.autotrack.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI : http://localhost:8080/swagger-ui.html (bouton « Authorize » pour coller le JWT). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI autotrackOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Dakar AutoTrack API").version("0.1.0")
                        .description("Passeport numérique des véhicules au terminal — prototype, données fictives."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
