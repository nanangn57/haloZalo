package app;

import analytics.access.PlatformAdminGate;
import identity.access.PlatformRoleLookup;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("app")
public class AppPlatformAdminConfiguration {
    @Bean
    PlatformAdminGate platformAdminGate(PlatformRoleLookup roles) {
        return roles::isAdmin;
    }
}
