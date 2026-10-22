package com.chubb.claims.auth.config;

import com.chubb.claims.auth.entity.Role;
import com.chubb.claims.auth.repository.UserRepository;
import com.chubb.claims.auth.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Creates one OFFICER and one ADMIN so staff features can be exercised; public registration only ever creates
 * CLAIMANTs. Development only: runs when {@code app.seed.demo-users=true} and is skipped for accounts that exist.
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "demo-users", havingValue = "true")
public class DemoUserSeeder implements ApplicationRunner {

    public static final String OFFICER_EMAIL = "officer@chubb.com";
    public static final String OFFICER_PASSWORD = "Officer@123";
    public static final String ADMIN_EMAIL = "admin@chubb.com";
    public static final String ADMIN_PASSWORD = "Admin@123";

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final UserService userService;
    private final UserRepository userRepository;

    public DemoUserSeeder(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("Demo Officer", OFFICER_EMAIL, OFFICER_PASSWORD, Role.OFFICER);
        seed("Demo Admin", ADMIN_EMAIL, ADMIN_PASSWORD, Role.ADMIN);
    }

    private void seed(String fullName, String email, String password, Role role) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userService.createUser(fullName, email, password, role);
        log.warn("Seeded demo {} account '{}'. Disable with app.seed.demo-users=false outside development.", role, email);
    }
}
