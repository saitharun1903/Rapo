package com.rideflow.mapper;

import com.rideflow.dto.driver.DriverResponse;
import com.rideflow.dto.driver.VehicleResponse;
import com.rideflow.entity.Driver;
import com.rideflow.entity.Vehicle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface DriverMapper {

    @Mapping(target = "id", source = "driver.id")
    @Mapping(target = "fullName", source = "driver.user.fullName")
    @Mapping(target = "email", source = "driver.user.email")
    @Mapping(target = "createdAt", source = "driver.createdAt")
    @Mapping(target = "vehicle", source = "vehicle")
    DriverResponse toResponse(Driver driver, Vehicle vehicle);

    VehicleResponse toResponse(Vehicle vehicle);
}
