package br.gov.defesa.mockgovbr.controller;

import br.gov.defesa.mockgovbr.model.MockUser;
import br.gov.defesa.mockgovbr.model.TokenResponse;
import br.gov.defesa.mockgovbr.service.KeyService;
import br.gov.defesa.mockgovbr.service.TokenService;
import br.gov.defesa.mockgovbr.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Controller
public class OidcController {

    private static final Logger log = LoggerFactory.getLogger(OidcController.class);

    private final UserService userService;
    private final TokenService tokenService;
    private final KeyService keyService;

    @Value("${mock.issuer:http://localhost:8089}")
    private String issuer;

    // Cache em memória de Authorization Codes emitidos
    private final Map<String, AuthCodeRecord> authCodes = new ConcurrentHashMap<>();

    private record AuthCodeRecord(
            MockUser user,
            String clientId,
            String redirectUri,
            String nonce,
            String codeChallenge,
            String codeChallengeMethod,
            boolean usedMfa,
            long createdAt
    ) {}

    public OidcController(UserService userService, TokenService tokenService, KeyService keyService) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.keyService = keyService;
    }

    // =========================================================================
    // 1. ENDPOINTS OIDC DE METADADOS E CHAVES PÚBLICAS
    // =========================================================================

    @GetMapping(value = "/.well-known/openid-configuration", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> openIdConfiguration() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("issuer", issuer);
        config.put("authorization_endpoint", issuer + "/authorize");
        config.put("token_endpoint", issuer + "/token");
        config.put("userinfo_endpoint", issuer + "/userinfo");
        config.put("jwks_uri", issuer + "/jwks.json");
        config.put("response_types_supported", List.of("code"));
        config.put("subject_types_supported", List.of("public"));
        config.put("id_token_signing_alg_values_supported", List.of("RS256"));
        config.put("scopes_supported", List.of(
                "openid", "email", "profile", "phone",
                "govbr_confiabilidades", "govbr_confiabilidades_idtoken", "govbr_empresa"
        ));
        config.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post", "none"));
        config.put("claims_supported", List.of(
                "sub", "cpf", "name", "social_name", "given_name", "family_name", "preferred_username",
                "email", "email_verified", "phone_number", "phone_number_verified",
                "picture", "profile", "birthdate", "updated_at",
                "nivel", "reliability_info", "reliabilities",
                "amr", "mfa_verified", "auth_time"
        ));
        config.put("code_challenge_methods_supported", List.of("S256", "plain"));
        return config;
    }

    @GetMapping(value = {"/jwks.json", "/jwk"}, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> jwks() {
        return keyService.getJwksMap();
    }

    // =========================================================================
    // 2. FLUXO DE LOGIN E AUTORIZAÇÃO (/authorize)
    // =========================================================================

    @GetMapping("/authorize")
    public String showLoginPage(
            @RequestParam(name = "response_type", defaultValue = "code") String responseType,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "scope", defaultValue = "openid") String scope,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "nonce", required = false) String nonce,
            @RequestParam(name = "code_challenge", required = false) String codeChallenge,
            @RequestParam(name = "code_challenge_method", required = false) String codeChallengeMethod,
            Model model) {

        log.info("Requisição de autorização recebida: client_id={}, redirect_uri={}, PKCE={}",
                clientId, redirectUri, codeChallenge != null);

        model.addAttribute("responseType", responseType);
        model.addAttribute("clientId", clientId != null ? clientId : "delfos-client");
        model.addAttribute("redirectUri", redirectUri != null ? redirectUri : "");
        model.addAttribute("scope", scope);
        model.addAttribute("state", state);
        model.addAttribute("nonce", nonce);
        model.addAttribute("codeChallenge", codeChallenge);
        model.addAttribute("codeChallengeMethod", codeChallengeMethod);

        return "login";
    }

    @PostMapping("/authorize")
    public String processLogin(
            @RequestParam("cpf") String cpf,
            @RequestParam("password") String password,
            @RequestParam(name = "clientId", required = false) String clientId,
            @RequestParam(name = "redirectUri", required = false) String redirectUri,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "nonce", required = false) String nonce,
            @RequestParam(name = "codeChallenge", required = false) String codeChallenge,
            @RequestParam(name = "codeChallengeMethod", required = false) String codeChallengeMethod,
            Model model) {

        log.info("Tentativa de login recebida para CPF: {}", cpf);

        Optional<MockUser> authUser = userService.authenticate(cpf, password);
        if (authUser.isEmpty()) {
            log.warn("Autenticação falhou para CPF: {}", cpf);
            model.addAttribute("error", "CPF ou senha incorretos.");
            model.addAttribute("cpf", cpf);
            model.addAttribute("clientId", clientId);
            model.addAttribute("redirectUri", redirectUri);
            model.addAttribute("state", state);
            model.addAttribute("nonce", nonce);
            model.addAttribute("codeChallenge", codeChallenge);
            model.addAttribute("codeChallengeMethod", codeChallengeMethod);
            return "login";
        }

        MockUser user = authUser.get();

        // Determinação do uso de Duplo Fator (2FA / MFA):
        // Se configurado explicitamente no users.json, respeita; senão, simula realisticamente (50% de chance).
        boolean usedMfa = (user.mfaEnabled() != null)
                ? user.mfaEnabled()
                : ThreadLocalRandom.current().nextBoolean();

        log.info("Cidadão autenticado: {} ({}) | Duplo Fator (2FA) utilizado: {}",
                user.name(), user.cleanCpf(), usedMfa);

        // Gera o authorization_code
        String code = "mock_code_" + UUID.randomUUID().toString();
        authCodes.put(code, new AuthCodeRecord(
                user,
                clientId,
                redirectUri,
                nonce,
                codeChallenge,
                codeChallengeMethod,
                usedMfa,
                System.currentTimeMillis()
        ));

        if (redirectUri == null || redirectUri.isBlank()) {
            model.addAttribute("successCode", code);
            model.addAttribute("user", user);
            return "login";
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", code);
        if (state != null && !state.isBlank()) {
            uriBuilder.queryParam("state", state);
        }

        return "redirect:" + uriBuilder.build().toUriString();
    }

    // =========================================================================
    // 3. TROCA DE CÓDIGO POR TOKENS (/token)
    // =========================================================================

    @PostMapping(value = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> exchangeToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestParam(name = "grant_type", defaultValue = "authorization_code") String grantType,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "client_id", required = false) String clientIdParam,
            @RequestParam(name = "client_secret", required = false) String clientSecretParam,
            @RequestParam(name = "code_verifier", required = false) String codeVerifier) {

        log.info("Endpoint /token acionado: grant_type={}, code={}", grantType, code);

        // Suporte tanto a Authorization: Basic <base64> quanto parâmetros de form client_id/client_secret
        String effectiveClientId = clientIdParam;
        String[] basicCreds = extractBasicAuth(authHeader);
        if (basicCreds != null) {
            effectiveClientId = basicCreds[0];
        }

        if (!"authorization_code".equalsIgnoreCase(grantType)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "unsupported_grant_type", "error_description", "Grant type suportado: authorization_code"));
        }

        if (code == null || !authCodes.containsKey(code)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "invalid_grant", "error_description", "Código de autorização inválido ou expirado"));
        }

        AuthCodeRecord record = authCodes.remove(code);

        // Validação estrita do PKCE (RFC 7636) caso code_challenge tenha sido enviado no /authorize
        if (!verifyPkce(record.codeChallenge(), record.codeChallengeMethod(), codeVerifier)) {
            log.warn("Falha na validação PKCE para code_verifier fornecido.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "invalid_grant", "error_description", "Falha na validação PKCE (code_verifier inválido)"));
        }

        try {
            TokenResponse tokens = tokenService.generateTokens(
                    record.user(),
                    record.clientId() != null ? record.clientId() : effectiveClientId,
                    record.nonce(),
                    record.usedMfa()
            );
            return ResponseEntity.ok(tokens);
        } catch (Exception e) {
            log.error("Erro ao gerar tokens OIDC Gov.BR: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "server_error", "error_description", "Falha interna ao gerar tokens"));
        }
    }

    // =========================================================================
    // 4. INFORMAÇÕES DO USUÁRIO (/userinfo)
    // =========================================================================

    @RequestMapping(value = "/userinfo", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> userInfo(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        log.info("Endpoint /userinfo acionado com Header: {}", authHeader);

        Optional<TokenService.UserSession> sessionOpt = tokenService.getSessionByAccessToken(authHeader);

        MockUser user;
        boolean usedMfa;
        List<String> amr;
        long authTime;

        if (sessionOpt.isPresent()) {
            TokenService.UserSession session = sessionOpt.get();
            user = session.user();
            usedMfa = session.usedMfa();
            amr = session.amr();
            authTime = session.authTime();
        } else {
            List<MockUser> all = userService.listAll();
            user = !all.isEmpty() ? all.get(0) : null;
            usedMfa = ThreadLocalRandom.current().nextBoolean();
            amr = usedMfa ? List.of("passwd", "captcha", "mfa", "otp_offline") : List.of("passwd");
            authTime = System.currentTimeMillis() / 1000;
        }

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid_token"));
        }

        // Construção do payload com os atributos estritamente preconizados na documentação oficial do Gov.BR
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", user.cleanCpf());
        claims.put("name", user.name());
        if (user.socialName() != null && !user.socialName().isBlank()) {
            claims.put("social_name", user.socialName());
        }
        claims.put("profile", user.resolvedProfile());
        claims.put("picture", issuer + "/userinfo/picture");
        if (user.isEmailVerified()) {
            claims.put("email", user.email());
        }
        claims.put("email_verified", user.isEmailVerified());
        if (user.isPhoneVerified()) {
            claims.put("phone_number", user.cleanPhoneNumber());
        }
        claims.put("phone_number_verified", user.isPhoneVerified());

        // Atributos de autenticação e confiabilidade (amr, reliability_info)
        claims.put("preferred_username", user.cleanCpf());
        claims.put("amr", amr);
        claims.put("mfa_verified", usedMfa);
        claims.put("auth_time", authTime);

        MockUser.ReliabilityInfo relInfo = user.resolvedReliabilityInfo();
        claims.put("reliability_info", Map.of(
                "level", relInfo.level(),
                "reliabilities", relInfo.reliabilities().stream()
                        .map(r -> Map.of("id", r.id(), "updatedAt", r.updatedAt()))
                        .toList()
        ));

        // Atributos adicionais mantidos para retrocompatibilidade
        claims.put("cpf", user.cleanCpf());
        claims.put("given_name", user.resolvedGivenName());
        claims.put("family_name", user.resolvedFamilyName());
        claims.put("birthdate", user.birthdate() != null ? user.birthdate() : "1980-01-01");
        claims.put("updated_at", user.resolvedUpdatedAt());
        claims.put("nivel", user.resolvedNivel());
        claims.put("reliabilities", user.resolvedReliabilities());

        return ResponseEntity.ok(claims);
    }

    // =========================================================================
    // 5. ENDPOINT DA FOTO DO CIDADÃO (/userinfo/picture)
    // =========================================================================

    @GetMapping(value = "/userinfo/picture", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public byte[] userPicture() {
        // Retorna um pixel PNG transparente 1x1 padrão
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");
    }

    // =========================================================================
    // 6. APIS REST OFICIAIS DE CONFIABILIDADE (Níveis e Selos)
    // =========================================================================

    @GetMapping(value = "/confiabilidades/v3/contas/{cpf}/niveis", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<Map<String, String>> getNiveis(@PathVariable("cpf") String cpf) {
        String clean = cpf.replaceAll("\\D", "");
        Optional<MockUser> userOpt = userService.findByCpf(clean);
        String levelId = "1"; // 1 = Bronze
        if (userOpt.isPresent()) {
            String lvl = userOpt.get().resolvedNivel();
            if ("Ouro".equalsIgnoreCase(lvl)) levelId = "3";
            else if ("Prata".equalsIgnoreCase(lvl)) levelId = "2";
        }
        return List.of(Map.of(
                "id", levelId,
                "dataAtualizacao", "2025-07-02 14:17:16"
        ));
    }

    @GetMapping(value = "/confiabilidades/v3/contas/{cpf}/confiabilidades", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<Map<String, String>> getConfiabilidades(@PathVariable("cpf") String cpf) {
        String clean = cpf.replaceAll("\\D", "");
        Optional<MockUser> userOpt = userService.findByCpf(clean);
        if (userOpt.isPresent()) {
            MockUser.ReliabilityInfo info = userOpt.get().resolvedReliabilityInfo();
            return info.reliabilities().stream()
                    .map(r -> Map.of("id", r.id(), "dataAtualizacao", r.updatedAt()))
                    .toList();
        }
        return List.of(Map.of("id", "201", "dataAtualizacao", "2025-07-02 14:17:16"));
    }

    // =========================================================================
    // 7. ENDPOINT DE LOGOUT (/logout)
    // =========================================================================

    @RequestMapping(value = "/logout", method = {RequestMethod.GET, RequestMethod.POST})
    public String logout(@RequestParam(name = "post_logout_redirect_uri", required = false) String postLogoutRedirectUri) {
        log.info("Logout Gov.BR solicitado. Redirect pós-logout: {}", postLogoutRedirectUri);
        if (postLogoutRedirectUri != null && !postLogoutRedirectUri.isBlank()) {
            return "redirect:" + postLogoutRedirectUri;
        }
        return "redirect:/authorize";
    }

    // =========================================================================
    // 8. API AUXILIAR PARA CONSULTA DE USUÁRIOS MOCKADOS
    // =========================================================================

    @GetMapping(value = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<MockUser> listUsers() {
        return userService.listAll();
    }

    // =========================================================================
    // MÉTODOS UTILITÁRIOS
    // =========================================================================

    private String[] extractBasicAuth(String authHeader) {
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String base64 = authHeader.substring(6).trim();
            byte[] decoded = Base64.getDecoder().decode(base64);
            String creds = new String(decoded, StandardCharsets.UTF_8);
            int colon = creds.indexOf(':');
            if (colon != -1) {
                return new String[]{creds.substring(0, colon), creds.substring(colon + 1)};
            }
        } catch (Exception e) {
            log.warn("Erro ao extrair credenciais Basic Auth: {}", e.getMessage());
        }
        return null;
    }

    private boolean verifyPkce(String codeChallenge, String codeChallengeMethod, String codeVerifier) {
        if (codeChallenge == null || codeChallenge.isBlank()) {
            return true; // PKCE não foi requerido na chamada authorize
        }
        if (codeVerifier == null || codeVerifier.isBlank()) {
            return false;
        }
        if ("plain".equalsIgnoreCase(codeChallengeMethod)) {
            return codeChallenge.equals(codeVerifier);
        }
        // Padrão S256: BASE64URL-ENCODE(SHA256(ASCII(code_verifier))) sem padding
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return codeChallenge.equals(computed);
        } catch (Exception e) {
            log.error("Erro ao validar PKCE: {}", e.getMessage());
            return false;
        }
    }
}
