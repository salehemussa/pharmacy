package com.pharmacy.security;

import com.pharmacy.user.UserAccount;
import com.pharmacy.user.UserAccountRepository;
import com.pharmacy.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserAccountRepository users;

    @Transactional(readOnly = true)
    public UserAccount requireEntity() {
        return users.findById(AuthContext.require().getId())
                .orElseThrow(() -> BusinessException.notFound("The signed-in user was not found."));
    }
}
