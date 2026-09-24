package com.rideflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Column(name = "make", nullable = false, length = 40)
    private String make;

    @Column(name = "model", nullable = false, length = 40)
    private String model;

    @Column(name = "color", nullable = false, length = 30)
    private String color;

    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;

    @Column(name = "model_year", nullable = false)
    private short modelYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    private VehicleCategory category;

    @Column(name = "seats", nullable = false)
    private short seats;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected Vehicle() {
    }

    private Vehicle(Driver driver, VehicleDetails details) {
        this.driver = driver;
        this.make = details.make();
        this.model = details.model();
        this.color = details.color();
        this.plateNumber = details.plateNumber();
        this.modelYear = details.modelYear();
        this.category = details.category();
        this.seats = details.seats();
        this.active = true;
    }

    public static Vehicle register(Driver driver, VehicleDetails details) {
        return new Vehicle(driver, details);
    }

    public void deactivate() {
        this.active = false;
    }

    /** Validated, normalised vehicle attributes. */
    public record VehicleDetails(
            String make, String model, String color, String plateNumber,
            short modelYear, VehicleCategory category, short seats) {
    }

    public UUID getId() {
        return id;
    }

    public Driver getDriver() {
        return driver;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public String getColor() {
        return color;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public short getModelYear() {
        return modelYear;
    }

    public VehicleCategory getCategory() {
        return category;
    }

    public short getSeats() {
        return seats;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Vehicle vehicle && id != null && id.equals(vehicle.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(Vehicle.class);
    }
}
