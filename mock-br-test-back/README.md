# Backend Consumidor de Teste — Gov.BR (`mock-br-test-back`)

Aplicação backend em **Java 21 e Spring Boot 3 com Spring Security OAuth2 Client**, configurada para atuar como o cliente confiável do **Gov.BR** (consumindo o simulador `mock-br` na porta 8089) e fornecendo a API de sessão para o frontend Angular (`mock-br-test-front` na porta 4200).

---

## 1. Arquitetura do Fluxo

```
[Angular :4200]
      |
      | 1. Redireciona para login
      v
[Spring Boot :8080] (/oauth2/authorization/govbr)
      |
      | 2. Redireciona para tela de login
      v
[Mock Gov.BR :8089] (/authorize)
      |
      | 3. Usuário autentica CPF e Senha
      v
[Spring Boot :8080] (/login/oauth2/code/govbr)
      |
      | 4. Troca de code por JWT no backchannel (:8089/token)
      | 5. Redireciona de volta para o Angular (:4200/?status=success)
      v
[Angular :4200]
      |
      | 6. GET /api/auth/me (com cookies de sessão)
      v
[Spring Boot :8080] -> Retorna dados do cidadão e status "Sucesso! Você está logado"
```

---

## 2. Como Executar

### Pré-requisitos
* Java 21 ou superior
* Apache Maven 3.9+
* O simulador `mock-br` em execução na porta `8089`

### Execução via Maven:
Na pasta `mock-br-test-back`:
```bash
mvn spring-boot:run
```

A aplicação subirá em: **`http://localhost:8080`**.

---

## 3. Endpoints REST Expostos

| Endpoint | Método | Descrição |
| :--- | :---: | :--- |
| `/oauth2/authorization/govbr` | GET | Ponto de entrada que inicia o fluxo OIDC junto ao Gov.BR |
| `/login/oauth2/code/govbr` | GET | Callback que recebe o `code` e valida o `id_token` |
| `/api/auth/me` | GET | Retorna o status de autenticação e os dados do cidadão (`OidcUser`) |
| `/api/auth/logout` | POST | Invalida a sessão HTTP e desconecta o usuário |
