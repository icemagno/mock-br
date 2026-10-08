package br.gov.defesa.mockgovbr;

import br.gov.defesa.mockgovbr.model.MockUser;
import br.gov.defesa.mockgovbr.service.KeyService;
import br.gov.defesa.mockgovbr.service.TokenService;
import br.gov.defesa.mockgovbr.service.UserService;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MockGovBrApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private KeyService keyService;

    @Autowired
    private TokenService tokenService;

    @Test
    void contextLoads() {
        List<MockUser> users = userService.listAll();
        assertThat(users).hasSizeGreaterThanOrEqualTo(15);
        assertThat(keyService.getRsaJWK()).isNotNull();
    }

    @Test
    void testOpenIdConfigurationEndpoint() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost:8089"))
                .andExpect(jsonPath("$.authorization_endpoint").value("http://localhost:8089/authorize"))
                .andExpect(jsonPath("$.token_endpoint").value("http://localhost:8089/token"))
                .andExpect(jsonPath("$.jwks_uri").value("http://localhost:8089/jwks.json"))
                .andExpect(jsonPath("$.claims_supported", hasItems(
                        "sub", "cpf", "name", "social_name", "given_name", "family_name",
                        "email", "phone_number", "birthdate", "nivel", "reliability_info", "amr", "mfa_verified"
                )));
    }

    @Test
    void testJwksEndpoint() throws Exception {
        mockMvc.perform(get("/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray());

        mockMvc.perform(get("/jwk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray());
    }

    @Test
    void testAuthorizePageRendering() throws Exception {
        mockMvc.perform(get("/authorize")
                        .param("client_id", "delfos-client")
                        .param("redirect_uri", "http://localhost:8080/login/oauth2/code/govbr"))
                .andExpect(status().isOk());
    }

    @Test
    void testTokenGenerationWithAndWithoutMfa() throws Exception {
        MockUser user = userService.listAll().get(0);

        // Teste com Duplo Fator (MFA = true)
        var tokensComMfa = tokenService.generateTokens(user, "test-client", "nonce123", true);
        assertThat(tokensComMfa.idToken()).isNotEmpty();
        assertThat(tokensComMfa.accessToken()).startsWith("ey"); // Valida que access_token é JWT assinado!

        SignedJWT parsedAccessToken = SignedJWT.parse(tokensComMfa.accessToken());
        assertThat(parsedAccessToken.getJWTClaimsSet().getSubject()).isEqualTo(user.cleanCpf());
        assertThat(parsedAccessToken.getJWTClaimsSet().getStringListClaim("amr")).contains("mfa", "otp_offline");

        var sessionComMfa = tokenService.getSessionByAccessToken(tokensComMfa.accessToken());
        assertThat(sessionComMfa).isPresent();
        assertThat(sessionComMfa.get().usedMfa()).isTrue();
        assertThat(sessionComMfa.get().amr()).contains("passwd", "mfa", "otp_offline");

        // Teste sem Duplo Fator (MFA = false)
        var tokensSemMfa = tokenService.generateTokens(user, "test-client", "nonce123", false);
        assertThat(tokensSemMfa.idToken()).isNotEmpty();
        assertThat(tokensSemMfa.accessToken()).startsWith("ey");

        var sessionSemMfa = tokenService.getSessionByAccessToken(tokensSemMfa.accessToken());
        assertThat(sessionSemMfa).isPresent();
        assertThat(sessionSemMfa.get().usedMfa()).isFalse();
        assertThat(sessionSemMfa.get().amr()).containsExactly("passwd");
    }

    @Test
    void testUserInfoEndpoint() throws Exception {
        MockUser user = userService.listAll().get(0);
        var tokens = tokenService.generateTokens(user, "test-client", "nonce123", true);

        mockMvc.perform(get("/userinfo")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value(user.cleanCpf()))
                .andExpect(jsonPath("$.name").value(user.name()))
                .andExpect(jsonPath("$.email").value(user.email()))
                .andExpect(jsonPath("$.reliability_info.level").value(user.resolvedReliabilityInfo().level()))
                .andExpect(jsonPath("$.reliability_info.reliabilities").isArray())
                .andExpect(jsonPath("$.amr").isArray());
    }

    @Test
    void testConfiabilidadesApis() throws Exception {
        MockUser user = userService.findByCpf("11122233344").orElseThrow();

        mockMvc.perform(get("/confiabilidades/v3/contas/" + user.cleanCpf() + "/niveis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("3"));

        mockMvc.perform(get("/confiabilidades/v3/contas/" + user.cleanCpf() + "/confiabilidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());
    }
}
