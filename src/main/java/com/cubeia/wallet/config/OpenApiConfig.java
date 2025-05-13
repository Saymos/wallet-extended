package com.cubeia.wallet.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;

/**
 * Configuration for OpenAPI documentation.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Cubeia Wallet API",
        version = "1.0.0",
        description = "REST API for a wallet service that allows account creation, " +
                      "balance checks, and transfers between accounts",
        contact = @Contact(
            name = "Cubeia",
            url = "https://cubeia.com",
            email = "info@cubeia.com"
        ),
        license = @License(
            name = "Apache 2.0",
            url = "https://www.apache.org/licenses/LICENSE-2.0.html"
        )
    ),
    servers = {
        @Server(
            url = "/",
            description = "Local server"
        )
    }
)
public class OpenApiConfig {
    // No additional configuration needed
} 