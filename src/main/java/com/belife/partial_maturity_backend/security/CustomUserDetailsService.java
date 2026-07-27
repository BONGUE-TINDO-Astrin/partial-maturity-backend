package com.belife.partial_maturity_backend.security;

import com.belife.partial_maturity_backend.entities.AppUserEntity;
import com.belife.partial_maturity_backend.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Permet à Spring Security de charger un utilisateur depuis SQL Server.
 *
 * <p>Le rôle stocké sous la forme ADMIN ou COMPTABILITE est transformé
 * automatiquement par Spring Security en ROLE_ADMIN ou
 * ROLE_COMPTABILITE.</p>
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    /**
     * Charge un utilisateur à partir de son identifiant de connexion.
     *
     * @param username identifiant saisi sur l'écran de connexion
     * @return représentation Spring Security de l'utilisateur
     * @throws UsernameNotFoundException si l'utilisateur n'existe pas
     */
    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);

        AppUserEntity appUser = appUserRepository
                .findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Identifiants incorrects."
                ));

        return User.builder()
                .username(appUser.getUsername())
                .password(appUser.getPasswordHash())
                .roles(appUser.getRole().name())
                .disabled(!appUser.isActive())
                .build();
    }
}