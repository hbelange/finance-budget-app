
package com.hbelange.financebudgetapp.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.hbelange.financebudgetapp.entity.User;
import com.hbelange.financebudgetapp.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class UserProvisioningFilter extends OncePerRequestFilter {
    private final UserRepository userRepository;

    public UserProvisioningFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String sub = jwtAuth.getName();
            if (!userRepository.existsById(sub)) {
                User user = new User();
                user.setSub(sub);
                userRepository.save(user);
            }
        } 

        filterChain.doFilter(request, response);
    }
}