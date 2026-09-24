package com.rideflow.mapper;

import com.rideflow.dto.user.UserResponse;
import com.rideflow.entity.User;
import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);
}
