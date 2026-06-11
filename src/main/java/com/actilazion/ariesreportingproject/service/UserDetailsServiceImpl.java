package com.actilazion.ariesreportingproject.service;

import com.actilazion.ariesreportingproject.repository.transaction.UserViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserViewRepository userViewRepository;
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userViewRepository.findByEmail(email)
                .map(u -> User.builder()
                        .username(u.getEmail())
                        .password("")
                        .roles(u.getRole())
                        .accountLocked(!u.getIsActive())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
