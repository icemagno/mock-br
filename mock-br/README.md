# Simulador de Autenticação OIDC Gov.BR (`mock-br`)

Este microsserviço é um **Mock Provider OpenID Connect (OIDC)** desenvolvido em **Java 21 e Spring Boot 3 com Spring Security**, projetado para simular o mecanismo oficial de autenticação de cidadãos do **Gov.BR** durante o desenvolvimento e testes locais, dispensando credenciamento prévio em homologação ou produção.

---

## 1. Princípios do Simulador

* **Fidelidade Estrita ao Padrão Gov.BR:** O Gov.BR não gerencia postos, cargos ou funções militares; ele autentica **cidadãos brasileiros (pessoas físicas)** e emite atributos cadastrais civis básicos e níveis de confiabilidade de conta (**Bronze, Prata e Ouro**). O cruzamento do CPF do cidadão com seus dados funcionais militares é de responsabilidade da base de dados interna do sistema consumidor (DELFOS).
* **Objeto de Usuário Completo:** Contempla todos os atributos preconizados na especificação oficial do Gov.BR (OpenID Connect / Escopo `profile` e `govbr_confiabilidades`).
* **Simulação de Duplo Fator (2FA / MFA):** Emite a claim oficial **`amr`** (*Authentication Methods References*). Caso o usuário utilize o duplo fator, `amr` conterá `["passwd", "mfa"]`; caso contrário, `["passwd"]`. O simulador efetua sorteio aleatório a cada sessão de login (ou respeita a configuração específica do usuário no JSON).
* **Interface Limpa:** A tela de login segue fielmente a interface de autenticação do portal gov.br, solicitando apenas o **CPF** e a **Senha** do cidadão.
* **Assinatura Criptográfica RSA (RS256):** Gera chaves públicas/privadas RSA (2048 bits) e expõe o endpoint JWKS (`/jwks.json`) para validação dos tokens JWT pelos Resource Servers e APIs.
* **Porta Padrão:** Executa na porta `8089`.

---

## 2. Atributos Oficiais Emitidos pelo Gov.BR

O `id_token` e o endpoint `/userinfo` emitem os atributos preconizados pela API oficial do Gov.BR:

| Atributo (Claim) | Tipo | Descrição Oficial |
| :--- | :---: | :--- |
| `sub` | String | CPF do cidadão (apenas dígitos) |
| `cpf` | String | CPF do cidadão (apenas dígitos) |
| `name` | String | Nome civil completo do cidadão |
| `given_name` | String | Primeiro nome |
| `family_name` | String | Sobrenome / nomes intermediários |
| `preferred_username` | String | CPF do cidadão |
| `email` | String | E-mail principal cadastrado |
| `email_verified` | Boolean | Indicador de e-mail verificado |
| `phone_number` | String | Telefone celular no formato internacional (E.164: `+55...`) |
| `phone_number_verified` | Boolean | Indicador de telefone verificado |
| `picture` | String | URL da foto do cidadão (obtida da base CNH/TSE) |
| `profile` | String | URL do perfil (`https://acesso.gov.br`) |
| `birthdate` | String | Data de nascimento no formato ISO (`AAAA-MM-DD`) |
| `updated_at` | Long | Timestamp Unix da última atualização cadastral |
| `nivel` | String | Nível da conta: **Bronze**, **Prata** ou **Ouro** |
| `reliability_info` | String | Nível da conta (compatibilidade de versões de API) |
| `reliabilities` | Array | Lista de selos oficiais (ex: Biometria TSE, CNH, Internet Banking, ICP-Brasil) |
| **`amr`** | Array | **Métodos de autenticação:** `["passwd", "mfa"]` ou `["passwd"]` |
| **`mfa_verified`** | Boolean | Indicador booleano de duplo fator utilizado |
| `auth_time` | Long | Timestamp Unix do instante da autenticação |

---

## 3. Gestão do Duplo Fator (2FA) e Aleatoriedade

Conforme especificado no manual do integrador Gov.BR:
1. **Sem Duplo Fator:** Quando o usuário efetua login apenas com senha:
   * `"amr": ["passwd"]`
   * `"mfa_verified": false`
2. **Com Duplo Fator:** Quando o usuário utiliza senha + OTP/aplicativo gov.br:
   * `"amr": ["passwd", "mfa"]`
   * `"mfa_verified": true`

No arquivo `users.json`, o campo `"mfa_enabled"` aceita:
* `null`: O simulador realiza um **sorteio aleatório a cada login** (50% de probabilidade de usar ou não 2FA);
* `true`: Força sempre a presença de 2FA para aquele CPF;
* `false`: Força sempre a ausência de 2FA para aquele CPF.

---

## 4. Base de Usuários Mockados (`src/main/resources/users.json`)

O simulador conta com **15 cidadãos brasileiros** pré-cadastrados cobrindo todos os níveis de confiabilidade (Bronze, Prata e Ouro), faixas etárias, e-mails, telefones e CPFs válidos. A senha padrão de todos os usuários é **`123`**.

### Exemplo de Registro:
```json
{
  "cpf": "11122233344",
  "password": "123",
  "name": "CARLOS ALBERTO DA SILVA",
  "given_name": "CARLOS",
  "family_name": "ALBERTO DA SILVA",
  "email": "carlos.silva@email.com",
  "email_verified": true,
  "phone_number": "+5561988881111",
  "phone_number_verified": true,
  "birthdate": "1975-04-12",
  "picture": "https://avatar.iran.liara.run/public/boy?username=carlos",
  "profile": "https://acesso.gov.br",
  "updated_at": 1712000000,
  "nivel": "Ouro",
  "mfa_enabled": null,
  "reliabilities": [
    "Biometria Facial TSE",
    "Validação Facial CNH",
    "Certificado Digital ICP-Brasil"
  ]
}
```

---

## 5. Como Executar

### Pré-requisitos
* Java 21 ou superior
* Apache Maven 3.9+

### Execução via Maven:
Na pasta `mock-br`:
```bash
mvn spring-boot:run
```

Ou para rodar os testes:
```bash
mvn test
```

A aplicação subirá em: **`http://localhost:8089`**.

---

## 6. Endpoints OIDC Expostos

| Endpoint | Método | Descrição |
| :--- | :---: | :--- |
| `/.well-known/openid-configuration` | GET | Metadados de autodescoberta do provedor OIDC |
| `/jwks.json` | GET | Chave pública RSA para validação de assinatura dos JWTs |
| `/authorize` | GET/POST | Tela de login do Gov.BR (CPF e Senha) e emissão do `code` |
| `/token` | POST | Troca de `authorization_code` por `id_token` (JWT) e `access_token` |
| `/userinfo` | GET/POST | Retorna todos os atributos oficiais do cidadão autenticado |
| `/api/users` | GET | Lista os 15 usuários cadastrados no `users.json` |
