# Frontend Consumidor de Teste — Gov.BR (`mock-br-test-front`)

Aplicação web em **Angular 18** desenvolvida para demonstrar o consumo da autenticação oficial do **Gov.BR** integrada através do backend Spring Boot (`mock-br-test-back`) e do simulador OIDC (`mock-br`).

---

## 1. Comportamento da Aplicação

1. **Estado Inicial (Não Autenticado):** Exibe a tela com o botão oficial **"Entrar com gov.br"**.
2. **Redirecionamento:** Ao clicar em entrar, redireciona o usuário para o backend (`http://localhost:8080/oauth2/authorization/govbr`), que orquestra o fluxo Authorization Code junto ao `mock-br` (`http://localhost:8089`).
3. **Tela de Sucesso:** Após o login, o usuário é redirecionado de volta para o Angular e a tela exibe:
   * **"Sucesso! Você está logado"**
   * Nome completo do cidadão
   * CPF formatado
   * E-mail e Telefone
   * Nível de Confiabilidade (**Selo Bronze, Prata ou Ouro**)
   * Indicação de uso de **Duplo Fator (2FA / MFA)**
   * Métodos de Autenticação (`amr`)
   * Lista de Selos de Confiabilidade adquiridos
4. **Tela de Erro:** Caso ocorra qualquer falha no fluxo ou cancelamento, exibe a tela de erro com opção de tentar novamente.
5. **Logout:** Botão para desconectar e invalidar a sessão.

---

## 2. Como Executar

### Pré-requisitos
* Node.js v18+ (instalado v22.17.0)
* npm instalado

### Instalação de Dependências e Execução:
Na pasta `mock-br-test-front`:
```bash
npm install
npm start
```

A aplicação subirá em: **`http://localhost:4200`**.
