package br.gov.defesa.mockgovbr.service;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.UUID;

@Service
public class KeyService {

    private static final Logger log = LoggerFactory.getLogger(KeyService.class);

    private RSAKey rsaJWK;
    private String keyId;

    @PostConstruct
    public void init() {
        try {
            log.info("Inicializando par de chaves RSA (2048 bits) para assinatura OIDC...");
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair keyPair = gen.generateKeyPair();

            this.keyId = "mock-govbr-key-" + UUID.randomUUID().toString().substring(0, 8);

            this.rsaJWK = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(this.keyId)
                    .build();

            log.info("Par de chaves RSA gerado com sucesso. KeyID: {}", this.keyId);
        } catch (Exception e) {
            log.error("Erro fatal ao gerar chaves RSA: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao gerar chaves RSA para o OIDC Provider", e);
        }
    }

    public RSAKey getRsaJWK() {
        return rsaJWK;
    }

    public String getKeyId() {
        return keyId;
    }

    public Map<String, Object> getJwksMap() {
        return new JWKSet(rsaJWK.toPublicJWK()).toJSONObject();
    }
}
