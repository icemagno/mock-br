package br.gov.defesa.mockgovbr.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MockUser(
    String cpf,
    String password,
    String name,
    @JsonProperty("social_name") String socialName,
    @JsonProperty("given_name") String givenName,
    @JsonProperty("family_name") String familyName,
    String email,
    @JsonProperty("email_verified") Boolean emailVerified,
    @JsonProperty("phone_number") String phoneNumber,
    @JsonProperty("phone_number_verified") Boolean phoneNumberVerified,
    String picture,
    String profile,
    String birthdate,
    @JsonProperty("updated_at") Long updatedAt,
    String nivel,                                     // Bronze, Prata, Ouro
    @JsonProperty("mfa_enabled") Boolean mfaEnabled,  // true, false, null (aleatório)
    @JsonProperty("reliability_info") ReliabilityInfo reliabilityInfo,
    List<String> reliabilities                         // Legado amigável
) {
    public record ReliabilityInfo(
        String level,                                 // bronze, silver, gold
        List<ReliabilityItem> reliabilities
    ) {}

    public record ReliabilityItem(
        String id,                                    // ex: "201", "602", "701", "801", "901"
        String updatedAt                              // ISO-8601 ex: "2025-07-02T14:17:16.001-0300"
    ) {}

    public String cleanCpf() {
        return cpf != null ? cpf.replaceAll("\\D", "") : "";
    }

    public String cleanPhoneNumber() {
        if (phoneNumber == null) return "61999999999";
        String digits = phoneNumber.replaceAll("\\D", "");
        if ((digits.length() == 12 || digits.length() == 13) && digits.startsWith("55")) {
            return digits.substring(2);
        }
        return digits;
    }

    public String resolvedGivenName() {
        if (givenName != null && !givenName.isBlank()) return givenName;
        if (name != null && !name.isBlank()) {
            return name.trim().split("\\s+")[0];
        }
        return "";
    }

    public String resolvedFamilyName() {
        if (familyName != null && !familyName.isBlank()) return familyName;
        if (name != null && !name.isBlank()) {
            String[] parts = name.trim().split("\\s+");
            if (parts.length > 1) {
                return String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
            }
        }
        return "";
    }

    public boolean isEmailVerified() {
        return emailVerified == null || emailVerified;
    }

    public boolean isPhoneVerified() {
        return phoneNumberVerified == null || phoneNumberVerified;
    }

    public String resolvedProfile() {
        return profile != null && !profile.isBlank() ? profile : "https://servicos.acesso.gov.br/";
    }

    public String resolvedPicture() {
        return picture != null && !picture.isBlank() ? picture : "https://sso.staging.acesso.gov.br/userinfo/picture";
    }

    public long resolvedUpdatedAt() {
        return updatedAt != null ? updatedAt : 1700000000L;
    }

    public String resolvedNivel() {
        if (reliabilityInfo != null && reliabilityInfo.level() != null) {
            return switch (reliabilityInfo.level().toLowerCase()) {
                case "gold" -> "Ouro";
                case "silver" -> "Prata";
                default -> "Bronze";
            };
        }
        return nivel != null ? nivel : "Bronze";
    }

    public ReliabilityInfo resolvedReliabilityInfo() {
        if (reliabilityInfo != null && reliabilityInfo.level() != null && reliabilityInfo.reliabilities() != null) {
            return reliabilityInfo;
        }
        if ("Ouro".equalsIgnoreCase(nivel)) {
            return new ReliabilityInfo("gold", List.of(
                new ReliabilityItem("701", "2025-07-02T14:17:16.001-0300"),
                new ReliabilityItem("801", "2025-08-19T16:41:24.853-0300")
            ));
        } else if ("Prata".equalsIgnoreCase(nivel)) {
            return new ReliabilityInfo("silver", List.of(
                new ReliabilityItem("602", "2025-07-02T14:17:16.001-0300"),
                new ReliabilityItem("301", "2025-08-19T16:41:24.853-0300")
            ));
        } else {
            return new ReliabilityInfo("bronze", List.of(
                new ReliabilityItem("201", "2025-07-02T14:17:16.001-0300")
            ));
        }
    }

    public List<String> resolvedReliabilities() {
        if (reliabilities != null && !reliabilities.isEmpty()) {
            return reliabilities;
        }
        if ("Ouro".equalsIgnoreCase(resolvedNivel())) {
            return List.of("Biometria Facial CNH/TSE (701)", "Certificado Digital ICP-Brasil (801)");
        } else if ("Prata".equalsIgnoreCase(resolvedNivel())) {
            return List.of("Validação Bancária Internet Banking (602)", "Cadastro Servidor Público SIGEPE (301)");
        } else {
            return List.of("Cadastro Básico Receita Federal e Previdência (201)");
        }
    }
}
