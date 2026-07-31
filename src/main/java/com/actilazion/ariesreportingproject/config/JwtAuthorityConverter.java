package com.actilazion.ariesreportingproject.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class JwtAuthorityConverter {
    public Collection<? extends GrantedAuthority> convert(UserDetails userDetails) {
        return userDetails.getAuthorities();
    }
}
