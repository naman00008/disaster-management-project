package com.reliefnet.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "volunteers")
public class Volunteer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "volunteer_skills", joinColumns = @JoinColumn(name = "volunteer_id"))
    @Column(name = "skill")
    private List<String> skills;

    @Enumerated(EnumType.STRING)
    private AvailabilityStatus availabilityStatus = AvailabilityStatus.AVAILABLE;

    private String currentLocation;
    private String organization;
    private int experienceYears;
    private String bio;

    private LocalDateTime registeredAt;

    @PrePersist
    protected void onCreate() { registeredAt = LocalDateTime.now(); }

    public enum AvailabilityStatus { AVAILABLE, BUSY, ON_MISSION, OFFLINE }

    // ── Constructors ──
    public Volunteer() {}

    // ── Getters & Setters ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }

    public AvailabilityStatus getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(AvailabilityStatus availabilityStatus) { this.availabilityStatus = availabilityStatus; }

    public String getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public int getExperienceYears() { return experienceYears; }
    public void setExperienceYears(int experienceYears) { this.experienceYears = experienceYears; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
}
