package com.smarttravel.config;

import com.smarttravel.entity.User;
import com.smarttravel.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedUsers();
    }

    private void seedUsers() {
        if (userRepository.existsByEmail("admin@smarttravel.vn")) return;

        User admin = new User();
        admin.setFullName("Admin SmartTravel");
        admin.setEmail("admin@smarttravel.vn");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setPhone("0901111111");
        admin.setRole(User.Role.ADMIN);
        userRepository.save(admin);

        User demo = new User();
        demo.setFullName("Demo User");
        demo.setEmail("demo@smarttravel.vn");
        demo.setPassword(passwordEncoder.encode("demo123"));
        demo.setPhone("0902222222");
        demo.setRole(User.Role.USER);
        userRepository.save(demo);

        System.out.println("Admin: admin@smarttravel.vn / admin123");
    }
}