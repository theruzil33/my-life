package com.theruzil.gateway.auth.dto;

import com.theruzil.gateway.user.User;

public record UserResponse(Long id, String email) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail());
    }
}
