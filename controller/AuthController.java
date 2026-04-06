package com.reliefnet.controller;

import com.reliefnet.model.User;
import com.reliefnet.model.Volunteer;
import com.reliefnet.repository.UserRepository;
import com.reliefnet.repository.VolunteerRepository;
import com.reliefnet.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired private UserRepository userRepo;
    @Autowired private VolunteerRepository volRepo;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtUtil jwtUtil;

    // ── LOGIN ────────────────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        String selectedRole = body.get("role"); // "admin", "user", "volunteer"

        if (email == null || password == null || selectedRole == null) {
            return ResponseEntity.badRequest().body(err("Email, password and role are required."));
        }

        Optional<User> optUser = userRepo.findByEmail(email.toLowerCase().trim());
        if (optUser.isEmpty()) {
            return ResponseEntity.status(401).body(err("No account found with that email address."));
        }

        User user = optUser.get();

        if (!encoder.matches(password, user.getPassword())) {
            return ResponseEntity.status(401).body(err("Incorrect password. Please try again."));
        }

        if (!user.isEnabled()) {
            return ResponseEntity.status(403).body(err("This account has been disabled."));
        }

        String actualRole = user.getRole().name().toLowerCase();
        if (!actualRole.equals(selectedRole.toLowerCase())) {
            return ResponseEntity.status(403).body(
                err("This account is registered as '" + actualRole + "', not '" + selectedRole + "'. Please select the correct role."));
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", token);
        resp.put("id", user.getId());
        resp.put("fullName", user.getFullName());
        resp.put("email", user.getEmail());
        resp.put("role", user.getRole().name().toLowerCase());
        resp.put("phone", user.getPhone());
        resp.put("address", user.getAddress());

        // If volunteer, include volunteer profile
        if (user.getRole() == User.Role.VOLUNTEER) {
            volRepo.findByUser(user).ifPresent(v -> {
                resp.put("volunteerId", v.getId());
                resp.put("skills", v.getSkills());
                resp.put("organization", v.getOrganization());
                resp.put("currentLocation", v.getCurrentLocation());
                resp.put("availabilityStatus", v.getAvailabilityStatus().name());
                resp.put("experienceYears", v.getExperienceYears());
            });
        }

        return ResponseEntity.ok(resp);
    }

    // ── REGISTER USER / VOLUNTEER ────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> body) {
        String email    = ((String) body.getOrDefault("email", "")).toLowerCase().trim();
        String password = (String) body.get("password");
        String fullName = (String) body.get("fullName");
        String roleStr  = (String) body.get("role");
        String phone    = (String) body.getOrDefault("phone", "");
        String address  = (String) body.getOrDefault("address", "");

        // Validation
        if (email.isEmpty() || password == null || fullName == null || roleStr == null) {
            return ResponseEntity.badRequest().body(err("fullName, email, password and role are required."));
        }
        if (password.length() < 6) {
            return ResponseEntity.badRequest().body(err("Password must be at least 6 characters."));
        }
        if (userRepo.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(err("Email already registered. Please login."));
        }

        User.Role role;
        try {
            role = User.Role.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(err("Invalid role. Choose: admin, user, or volunteer."));
        }

        // Only ADMIN can create another ADMIN
        if (role == User.Role.ADMIN) {
            return ResponseEntity.status(403).body(err("Admin accounts cannot be self-registered."));
        }

        // Create user
        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPassword(encoder.encode(password));
        user.setPhone(phone);
        user.setAddress(address);
        user.setRole(role);
        userRepo.save(user);

        // If volunteer, create volunteer profile
        if (role == User.Role.VOLUNTEER) {
            Volunteer vol = new Volunteer();
            vol.setUser(user);
            vol.setOrganization((String) body.getOrDefault("organization", ""));
            vol.setCurrentLocation((String) body.getOrDefault("currentLocation", ""));
            vol.setBio((String) body.getOrDefault("bio", ""));
            vol.setExperienceYears(Integer.parseInt(String.valueOf(body.getOrDefault("experienceYears", "0"))));
            Object skillsObj = body.get("skills");
            if (skillsObj instanceof List) {
                vol.setSkills((List<String>) skillsObj);
            }
            vol.setAvailabilityStatus(Volunteer.AvailabilityStatus.AVAILABLE);
            volRepo.save(vol);
        }

        return ResponseEntity.ok(ok("Registration successful! You can now login."));
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────
    private Map<String, String> err(String msg) {
        return Map.of("error", msg);
    }
    private Map<String, String> ok(String msg) {
        return Map.of("message", msg);
    }
}
