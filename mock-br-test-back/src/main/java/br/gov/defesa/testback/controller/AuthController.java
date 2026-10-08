package br.gov.defesa.testback.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        Map<String, Object> response = new LinkedHashMap<>();

        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            response.put("authenticated", false);
            return ResponseEntity.ok(response);
        }

        List<String> amr = oidcUser.getAttribute("amr");
        boolean mfaVerified = (amr != null && amr.contains("mfa"))
                || Boolean.TRUE.equals(oidcUser.getAttribute("mfa_verified"));

        response.put("authenticated", true);
        response.put("sub", oidcUser.getSubject());
        response.put("cpf", oidcUser.getAttribute("cpf"));
        response.put("name", oidcUser.getAttribute("name"));
        response.put("social_name", oidcUser.getAttribute("social_name"));
        response.put("given_name", oidcUser.getAttribute("given_name"));
        response.put("family_name", oidcUser.getAttribute("family_name"));
        response.put("email", oidcUser.getAttribute("email"));
        response.put("phone_number", oidcUser.getAttribute("phone_number"));
        response.put("picture", oidcUser.getAttribute("picture"));
        response.put("birthdate", oidcUser.getAttribute("birthdate"));
        response.put("nivel", oidcUser.getAttribute("nivel"));
        response.put("reliability_info", oidcUser.getAttribute("reliability_info"));
        response.put("reliabilities", oidcUser.getAttribute("reliabilities"));
        response.put("amr", amr);
        response.put("mfa_verified", mfaVerified);
        response.put("auth_time", oidcUser.getAttribute("auth_time"));

        log.info("Sessão ativa consultada com sucesso para CPF: {} ({}) | Nível: {} | 2FA (AMR): {}",
                oidcUser.getAttribute("cpf"),
                oidcUser.getAttribute("name"),
                oidcUser.getAttribute("nivel"),
                amr);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/login-url")
    public ResponseEntity<Map<String, String>> getLoginUrl() {
        return ResponseEntity.ok(Map.of("loginUrl", "http://localhost:8080/oauth2/authorization/govbr"));
    }
}
