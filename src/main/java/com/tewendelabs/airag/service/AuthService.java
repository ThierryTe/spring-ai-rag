package com.tewendelabs.airag.service;

import java.util.List;
import java.util.UUID;

import com.tewendelabs.airag.exceptions.InvalidCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tewendelabs.airag.dto.AuthMeResponse;
import com.tewendelabs.airag.dto.DepartmentSummary;
import com.tewendelabs.airag.entity.Department;
import com.tewendelabs.airag.entity.User;
import com.tewendelabs.airag.exceptions.ResourceNotFoundException;
import com.tewendelabs.airag.repository.DepartmentRepository;
import com.tewendelabs.airag.repository.UserRepository;
import com.tewendelabs.airag.security.JwtService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AccessScopeResolver accessScopeResolver;
    private final DepartmentRepository departmentRepository;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
            AccessScopeResolver accessScopeResolver, DepartmentRepository departmentRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.accessScopeResolver = accessScopeResolver;
        this.departmentRepository = departmentRepository;
    }

    public String login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return jwtService.generateToken(user.getId());
    }

    /**
     * Le JWT ne porte que l'userId (voir JwtService) : le frontend doit passer par cet endpoint
     * apres login pour connaitre le role et le perimetre departements de l'utilisateur courant,
     * la meme resolution que celle utilisee par DepartmentAccessGuard/DocumentService.
     */
    public AuthMeResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        List<Integer> departmentIds = accessScopeResolver.resolveAuthorizedDepartmentIds(user);
        // Departements resolus en entites completes (pas juste les ids) : le frontend affiche le
        // libelle (ex. "Ressources humaines"), il n'a pas sa propre table de reference locale.
        List<DepartmentSummary> allowedDepartments = departmentRepository.findAllById(departmentIds).stream()
                .map(d -> new DepartmentSummary(d.getId(), d.getCode(), d.getLabel()))
                .toList();
        return new AuthMeResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().getCode(),
                allowedDepartments);
    }
}
