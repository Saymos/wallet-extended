package com.cubeia.wallet;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import org.springframework.boot.SpringApplication;

class WalletApplicationTest {

    @Test
    void main_ShouldStartApplication() {
        // This test is just to achieve code coverage for the main method
        // We don't assert anything, just ensure it runs without exceptions
        String[] args = {};
        
        try (var mockedSpringApplication = mockStatic(SpringApplication.class)) {
            WalletApplication.main(args);
            mockedSpringApplication.verify(() -> 
                SpringApplication.run(eq(WalletApplication.class), any(String[].class)));
        }
    }
} 