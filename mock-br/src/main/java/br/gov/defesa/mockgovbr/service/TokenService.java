package br.gov.defesa.mockgovbr.service;

import br.gov.defesa.mockgovbr.model.MockUser;
import br.gov.defesa.mockgovbr.model.TokenResponse;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final KeyService keyService;
    private final UserService userService;

    @Value("${mock.issuer:http://localhost:8089}")
    private String issuer;

    public record UserSession(
            MockUser user,
            boolean usedMfa,
            List<String> amr,
            long authTime
    ) {}

    // Cache em memória de access_token -> UserSession
    private final Map<String, UserSession> tokenSessionMap = new ConcurrentHashMap<>();

    public TokenService(KeyService keyService, UserService userService) {
        this.keyService = keyService;
        this.userService = userService;
    }

    public TokenResponse generateTokens(MockUser user, String clientId, String nonce, boolean usedMfa) throws Exception {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date atExp = new Date(nowMillis + 3600 * 1000); // 1 hora de expiração para Access Token
        Date idExp = new Date(nowMillis + 300 * 1000);  // 5 minutos para ID Token (Gov.BR especifica expiração curta)
        long authTimeSeconds = nowMillis / 1000;

        String audience = (clientId != null && !clientId.isBlank()) ? clientId : "delfos-client";

        // AMR estritamente conforme a documentação oficial do Gov.BR (Passo 9 e Presença do 2FA):
        // Com 2FA ativado: ["passwd", "captcha", "mfa", "otp_offline"]
        // Sem 2FA: ["passwd"]
        List<String> amr = usedMfa
                ? List.of("passwd", "captcha", "mfa", "otp_offline")
                : List.of("passwd");

        List<String> scopes = List.of("phone", "openid", "profile", "email", "govbr_confiabilidades", "govbr_confiabilidades_idtoken");

        // =====================================================================
        // 1. GERAÇÃO DO ACCESS_TOKEN COMO JWT ASSINADO RS256 (Especificação Oficial Gov.br)
        // =====================================================================
        JWTClaimsSet.Builder atClaims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(user.cleanCpf())
                .audience(audience)
                .issueTime(now)
                .expirationTime(atExp)
                .jwtID(UUID.randomUUID().toString())
                .claim("preferred_username", user.cleanCpf())
                .claim("scope", scopes)
                .claim("amr", amr);

        SignedJWT signedAccessToken = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(keyService.getKeyId())
                        .build(),
                atClaims.build()
        );
        signedAccessToken.sign(new RSASSASigner(keyService.getRsaJWK()));
        String accessToken = signedAccessToken.serialize();

        // =====================================================================
        // 2. GERAÇÃO DO ID_TOKEN COMO JWT ASSINADO RS256 (Especificação Oficial Gov.br)
        // =====================================================================
        MockUser.ReliabilityInfo relInfo = user.resolvedReliabilityInfo();
        Map<String, Object> relInfoMap = new LinkedHashMap<>();
        relInfoMap.put("level", relInfo.level());
        relInfoMap.put("reliabilities", relInfo.reliabilities().stream()
                .map(r -> Map.of("id", r.id(), "updatedAt", r.updatedAt()))
                .toList());

        JWTClaimsSet.Builder idClaims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(user.cleanCpf())
                .audience(audience)
                .issueTime(now)
                .expirationTime(idExp)
                .claim("auth_time", authTimeSeconds)
                .jwtID(UUID.randomUUID().toString())
                .claim("name", user.name())
                .claim("preferred_username", user.cleanCpf())
                .claim("profile", user.resolvedProfile())
                .claim("picture", issuer + "/userinfo/picture")
                .claim("scope", scopes)
                .claim("amr", amr)
                .claim("reliability_info", relInfoMap);

        if (user.socialName() != null && !user.socialName().isBlank()) {
            idClaims.claim("social_name", user.socialName());
        }

        if (user.isEmailVerified()) {
            idClaims.claim("email", user.email());
        }
        idClaims.claim("email_verified", user.isEmailVerified());

        if (user.isPhoneVerified()) {
            idClaims.claim("phone_number", user.cleanPhoneNumber());
        }
        idClaims.claim("phone_number_verified", user.isPhoneVerified());

        if (nonce != null && !nonce.isBlank()) {
            idClaims.claim("nonce", nonce);
        }

        // Claims complementares mantidas para conveniência e compatibilidade com consumidores
        idClaims.claim("cpf", user.cleanCpf());
        idClaims.claim("given_name", user.resolvedGivenName());
        idClaims.claim("family_name", user.resolvedFamilyName());
        idClaims.claim("birthdate", user.birthdate() != null ? user.birthdate() : "1980-01-01");
        idClaims.claim("updated_at", user.resolvedUpdatedAt());
        idClaims.claim("nivel", user.resolvedNivel());
        idClaims.claim("reliabilities", user.resolvedReliabilities());
        idClaims.claim("mfa_verified", usedMfa);

        SignedJWT signedIdToken = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(keyService.getKeyId())
                        .build(),
                idClaims.build()
        );
        signedIdToken.sign(new RSASSASigner(keyService.getRsaJWK()));
        String idToken = signedIdToken.serialize();

        // Registra sessão
        tokenSessionMap.put(accessToken, new UserSession(user, usedMfa, amr, authTimeSeconds));

        log.info("Tokens Gov.BR OIDC emitidos com sucesso! CPF: {} | 2FA (AMR): {} | Nível: {}",
                user.cleanCpf(), amr, relInfo.level());

        return new TokenResponse(
                accessToken,
                "Bearer",
                3600,
                idToken,
                "phone openid profile email govbr_confiabilidades govbr_confiabilidades_idtoken"
        );
    }

    @SuppressWarnings("unchecked")
    public Optional<UserSession> getSessionByAccessToken(String accessTokenHeader) {
        if (accessTokenHeader == null || accessTokenHeader.isBlank()) return Optional.empty();
        String token = accessTokenHeader.replace("Bearer ", "").trim();

        if (tokenSessionMap.containsKey(token)) {
            return Optional.of(tokenSessionMap.get(token));
        }

        // Resolução alternativa: decodificação direta do JWT do Access Token
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            String cpf = jwt.getJWTClaimsSet().getSubject();
            List<String> amr = (List<String>) jwt.getJWTClaimsSet().getClaim("amr");
            boolean usedMfa = amr != null && amr.contains("mfa");
            long authTime = jwt.getJWTClaimsSet().getIssueTime() != null
                    ? jwt.getJWTClaimsSet().getIssueTime().getTime() / 1000
                    : System.currentTimeMillis() / 1000;

            return userService.findByCpf(cpf)
                    .map(u -> new UserSession(u, usedMfa, amr != null ? amr : List.of("passwd"), authTime));
        } catch (Exception e) {
            log.warn("Falha ao recuperar sessão do access token JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
