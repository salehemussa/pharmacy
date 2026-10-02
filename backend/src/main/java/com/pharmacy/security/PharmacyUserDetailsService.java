package com.pharmacy.security;

import com.pharmacy.user.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PharmacyUserDetailsService {

    private final UserAccountRepository users;

    public UserPrincipal loadById(Long id) {
        return users.findWithRolesById(id)
                .map(user -> {
                    if (!user.isActive()) {
                        throw new DisabledException("Account is deactivated");
                    }
                    return UserPrincipal.from(user);
                })
                .orElseThrow(() -> new DisabledException("Account is not available"));
    }
}
