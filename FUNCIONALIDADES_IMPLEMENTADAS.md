# Funcionalidades Implementadas

## 1. Visão geral

A Atividade 5 evolui a tela Angular da Atividade 4 com autenticação JWT no Spring Boot e persistência SQL Server. O formulário, FormsModule, ngModel e CSS original foram preservados. Os logs de e-mail/senha foram removidos. Cadastro e recuperação de senha não foram implementados.

## 2. Arquitetura

```text
Angular 22 standalone
  ↓ HTTP (credenciais no login; Bearer nas próximas chamadas)
Spring Boot 3.5.16
  ↓ Spring Security / DTO / service
Spring Data JPA
  ↓ parâmetros JDBC
Microsoft SQL Server
```

SecurityFilterChain é executada antes dos controllers. Flyway cuida do esquema; o Hibernate somente valida. Não há sessão HTTP de autenticação. O rate limit mantém contadores em memória, o que não equivale a manter sessão de usuário.

## 3. Estrutura do projeto

```text
src/app/
  app.config.ts
  app.routes.ts
  auth/{api.config.ts,auth.service.ts,auth.interceptor.ts,auth.guard.ts}
  login/{login.ts,login.html,login.css}
  profile/{profile.ts,profile.html,profile.service.ts}
backend/
  pom.xml, mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties
  src/main/java/br/uesc/login/
    LoginApplication.java
    config/{SecurityConfig,JwtProperties,DemoUserInitializer}.java
    controller/{AuthController,ProfileController}.java
    dto/{LoginRequest,LoginResponse,ProfileResponse,ApiError}.java
    entity/AppUser.java
    exception/GlobalExceptionHandler.java
    repository/UserRepository.java
    security/{JwtService,JwtAuthenticationFilter,SecurityErrorWriter,
              LoginRateLimiter,LoginRateLimitFilter}.java
    service/AuthenticationService.java
  src/main/resources/application.properties
  src/main/resources/db/migration/V1__create_users.sql
  src/test/java/br/uesc/login/
    AuthenticationApiTest.java
    DemoUserInitializerTest.java
    JwtServiceTest.java
    LoginRateLimiterTest.java
.env.example, docker-compose.yml
README.md, FUNCIONALIDADES_IMPLEMENTADAS.md, GUIA_APRESENTACAO.md
```

## 4. Fluxo de autenticação

1. Angular valida formato/obrigatoriedade e limite da senha.
2. Bloqueia novos envios e inicia timeout de 10 segundos.
3. AuthService envia `POST /auth/login` com `email` e `password`.
4. RateLimitFilter verifica a janela do IP; excesso recebe 429.
5. Controller valida o DTO com Bean Validation.
6. Service normaliza o e-mail e consulta UserRepository.
7. BCrypt compara a senha com o hash. Usuário inexistente, desabilitado ou senha errada recebem o mesmo 401.
8. JwtService emite token com ID do usuário, emissor, emissão e expiração.
9. Angular recebe `accessToken/tokenType/expiresIn`, guarda em memória, limpa o campo de senha e abre `/perfil`.
10. Interceptor envia Bearer no GET protegido. JwtAuthenticationFilter valida o token e consulta o usuário atual.
11. SecurityContext recebe principal/authorities. SecurityFilterChain exige ROLE_USER e permite o controller de perfil.

A senha não acompanha o GET de perfil; o JWT não contém senha, hash ou e-mail.

## 5. Banco de dados

Tabela `app_users`:

| Campo | Tipo SQL Server | Responsabilidade |
|---|---|---|
| id | BIGINT IDENTITY, PK | Identificador usado no subject |
| email | VARCHAR(254), UNIQUE, NOT NULL | Identificação no login |
| password_hash | VARCHAR(60), NOT NULL | Hash BCrypt |
| role | VARCHAR(20), NOT NULL | Permissão, inicialmente USER |
| enabled | BIT, NOT NULL | Permite desativar acesso |
| created_at | DATETIME2(6), NOT NULL | Criação, registrada em UTC pela aplicação |

Migration V1 reproduz a tabela e a restrição única. Compose cria o banco vazio; Flyway cria a tabela durante startup. Em instalação própria, o banco deve existir. `ddl-auto=validate` detecta incompatibilidade de esquema.

DemoUserInitializer é opt-in por variável de ambiente. Cria somente se o e-mail não existir, normaliza minúsculas, aplica BCrypt e não sobrescreve usuário já existente. Nenhuma credencial de demonstração é fixada na aplicação.

## 6. BCrypt

Senha é um segredo; hash permite verificar uma tentativa sem armazenar seu valor original. BCrypt gera salt e inclui os parâmetros no próprio hash. O bean PasswordEncoder usa custo 12; o initializer chama `encode` e AuthenticationService chama `matches`.

A senha aceita no máximo 72 bytes UTF-8, incluindo verificação adicional para caracteres multibyte. A senha de demonstração exige pelo menos 12 caracteres. A comparação fictícia para e-mail inexistente reduz diferenças de tempo, sem prometer tempo perfeitamente constante.

## 7. JWT

JwtService usa JJWT 0.12.6 e HS256. JWT_SECRET é Base64 de pelo menos 32 bytes aleatórios. Chave ausente/fraca impede inicialização; não há fallback de desenvolvimento.

Claims: `iss=atividade5`, `sub=id`, `iat` e `exp`. A role é consultada no banco em cada chamada. Expiração padrão 900 segundos; configuração permite 60–3600.

A validação verifica assinatura, emissor, algoritmo HS256, expiração, presença de emissão e expiração e subject numérico. JWT é legível: assinatura garante integridade/autenticidade, não confidencialidade. Não existe refresh token.

## 8. Spring Security

SecurityConfig define:

- `SessionCreationPolicy.STATELESS`.
- POST /auth/login público.
- GET /api/profile exige ROLE_USER.
- Demais endpoints negados.
- Form login, HTTP Basic, request cache e logout de sessão desabilitados.
- CORS restrito; CSRF desabilitado para Bearer sem cookie automático.
- AuthenticationEntryPoint → 401; AccessDeniedHandler → 403.
- Headers padrão preservados.
- Filtros instanciados apenas na cadeia Security, evitando registro duplicado no container servlet.

O usuário automático do Spring foi excluído; a aplicação usa seu próprio service BCrypt/JWT.

## 9. JWT Filter

JwtAuthenticationFilter ignora o POST de login, lê Authorization e aceita esquema Bearer. Cabeçalho malformado ou token inválido/expirado recebe 401. Sem token, continua a cadeia para que as regras de autorização emitam 401.

Após validar token, busca ID no banco, exige `enabled` e monta uma authority `ROLE_` + role atual. Preenche SecurityContext com o e-mail como principal e credenciais nulas.

Problemas inesperados nessa etapa recebem 500 genérico. SecurityErrorWriter escreve erros de filtros; RestControllerAdvice trata os erros dos controllers. Isso cobre as duas fases, pois advice não captura automaticamente erros anteriores ao MVC.

## 10. Integração Angular

- `app.config.ts`: provideHttpClient com interceptor funcional e provideRouter.
- `AuthService`: POST, timeout, armazenamento em memória, expiração, estado e logout.
- `authInterceptor`: restringe destino por origem e caminho /api/, anexa Bearer e trata 401 sem confundir login inválido com sessão expirada.
- `authGuard`: retorna UrlTree para login quando não há token. Não é barreira de segurança da API.
- `Login`: mantém FormsModule/ngModel, usa signals para loading/erro no Angular standalone, bloqueia submits e limpa senha após resposta.
- `ProfileService/Profile`: GET protegido, estados de carregamento/erro, atualização e logout.
- `api.config.ts`: URL local da API e timeout de 10.000 ms.
- `app.routes.ts`: /login, /perfil protegido e redirecionamento padrão.

O token não vai para localStorage, sessionStorage ou cookies. Reload encerra o acesso local. Um timer encerra a sessão ao vencer o prazo informado; o servidor continua sendo a autoridade sobre validade.

## 11. Proteções implementadas

### BCrypt

**Problema:** exposição de senhas em vazamento do banco. **Implementação:** hash com salt, custo 12 e matches. **Onde:** SecurityConfig, DemoUserInitializer, AuthenticationService. **Limitação:** não substitui senha forte e proteção do banco; custo aumenta o trabalho de login.

### SQL Injection

**Problema:** input virar comando SQL. **Implementação:** método derivado parametrizado findByEmail, sem concatenação. **Onde:** UserRepository. **Limitação:** futuras queries devem manter parametrização; JPA não torna SQL manual concatenado seguro.

### Validação de entrada

**Problema:** campos ausentes, e-mail inválido e senha excessiva. **Implementação:** NotBlank/Email/Size, AssertTrue para 72 bytes UTF-8, validação Angular equivalente. **Onde:** LoginRequest e login.ts/html. **Limitação:** validação de campos ocorre após leitura do JSON; não há limite global dedicado do corpo HTTP.

### JWT expiration

**Problema:** uso indefinido do token. **Implementação:** exp obrigatório, padrão 900 s; parser verifica expiração. **Onde:** JwtService, JwtProperties e AuthService. **Limitação:** token pode ser reutilizado até expirar; relógios do servidor precisam estar corretos.

### JWT signature

**Problema:** adulterar subject ou exp. **Implementação:** HS256, verifyWith, emissor esperado e chave aleatória via ambiente. **Onde:** JwtService. **Limitação:** quem obtiver a chave consegue assinar tokens; não há rotação automatizada.

### Rate limiting

**Problema:** excesso de tentativas/brute force. **Implementação:** 10 tentativas / 60 s por remoteAddr, janela fixa, 429 com Retry-After, remoção de janelas vencidas, mapa limitado a 10.000 IPs. **Onde:** LoginRateLimiter/Filter. **Limitação:** processo único, reinício zera contadores, NAT compartilha cota, não confiar em X-Forwarded-For; no limite de memória novos IPs recebem 429.

### Prevenção de múltiplos submits

**Problema:** cliques repetidos causarem chamadas simultâneas. **Implementação:** guarda isLoading + botão/campos desabilitados + finalize. **Onde:** Login. **Limitação:** protege UX; clientes externos continuam sujeitos somente ao rate limit do backend.

### Timeout

**Problema:** UI ficar aguardando indefinidamente. **Implementação:** RxJS timeout(10.000) e mensagem amigável; pool/driver/JPA têm limites próprios. **Onde:** api.config.ts, AuthService, ProfileService, application.properties e DB_URL de exemplo. **Limitação:** timeout no cliente não garante interrupção de operações já iniciadas no servidor.

### CORS

**Problema:** origens de navegador não autorizadas lerem respostas. **Implementação:** uma origem configurável, GET/POST/OPTIONS, cabeçalhos permitidos e sem cookies. **Onde:** SecurityConfig. **Limitação:** não é autenticação e não bloqueia Postman/curl.

### Tratamento de erros

**Problema:** vazar SQL, stack traces e detalhes internos. **Implementação:** ApiError e mensagens fixas em advice/filtros; detalhes de erro padrão desabilitados. **Onde:** GlobalExceptionHandler, SecurityErrorWriter, application.properties. **Limitação:** erros de CORS são tratados pelo próprio Spring; diagnóstico operacional detalhado não foi implementado.

### Variáveis de ambiente

**Problema:** segredos fixados/versionados. **Implementação:** DB_*, JWT_SECRET e DEMO_USER_PASSWORD sem credenciais padrão; .env.example vazio para senhas. **Onde:** application.properties, Compose, .env.example e .gitignore. **Limitação:** ambiente também deve ser protegido; .gitignore não remove segredos previamente commitados.

### Ausência de senha em logs

**Problema:** console ou log expor credenciais. **Implementação:** remoção dos logs originais; aplicação não imprime senha/hash/token; DTOs sensíveis têm toString redigido; MockMvc não imprime requests em falhas de teste. **Onde:** Login, LoginRequest/Response, JwtProperties, DemoProperties e AuthenticationApiTest. **Limitação:** não habilitar logging HTTP/SQL detalhado com dados sensíveis; ferramentas externas e DevTools podem exibir tráfego.

### Endpoints protegidos

**Problema:** acesso anônimo ou sem authority. **Implementação:** SecurityFilterChain, validação JWT e consulta de enabled/role no banco. **Onde:** SecurityConfig/JwtAuthenticationFilter. **Limitação:** guard do Angular é somente navegação; logout não revoga token copiado.

### CSRF

**Problema:** navegador enviar credencial automaticamente numa requisição induzida. **Implementação:** API sem cookies/sessão/Basic e Bearer anexado explicitamente; csrf desabilitado nesse desenho. **Onde:** SecurityConfig. **Limitação:** introduzir cookies de autenticação exige redesenhar CSRF; XSS é outro problema.

### Segurança do armazenamento do token

**Problema:** token persistir desnecessariamente no navegador. **Implementação:** signal privado em memória, timer e limpeza no logout. **Onde:** AuthService. **Limitação:** reload perde acesso e XSS pode agir durante a sessão; memória não é cofre inviolável.

## 12. Endpoints

| Método | Endpoint | Autenticação | Função |
|---|---|---|---|
| POST | /auth/login | Público, rate limit | Validar credenciais e emitir JWT |
| GET | /api/profile | Bearer + ROLE_USER | Dados básicos do usuário autenticado |

## 13. Arquivos principais

| Arquivo | Responsabilidade |
|---|---|
| src/app/login/login.ts/html/css | Evolução do formulário original |
| src/app/auth/auth.service.ts | Login, token em memória, expiração e logout |
| src/app/auth/auth.interceptor.ts | Bearer restrito à API e tratamento de 401 |
| src/app/auth/auth.guard.ts | Proteção de navegação |
| src/app/profile/profile.service.ts | Chamada ao endpoint protegido |
| backend/.../controller/AuthController.java | Contrato HTTP do login |
| backend/.../service/AuthenticationService.java | Busca + BCrypt + emissão |
| backend/.../security/JwtService.java | Geração/validação de JWT |
| backend/.../security/JwtAuthenticationFilter.java | SecurityContext |
| backend/.../config/SecurityConfig.java | Regras, BCrypt, CORS e cadeia |
| backend/.../security/LoginRateLimiter.java | Janela de tentativas |
| backend/.../repository/UserRepository.java | Persistência parametrizada |
| backend/.../config/DemoUserInitializer.java | Provisionamento opt-in |
| backend/src/main/resources/db/migration/V1__create_users.sql | Esquema SQL Server |
| backend/src/main/resources/application.properties | Configuração do ambiente |
| docker-compose.yml / .env.example | Infraestrutura local e modelo de variáveis |

Nas linhas abreviadas com backend/..., o prefixo completo é `backend/src/main/java/br/uesc/login/`.

## 14. Testes

Executados em 28/09/2026: 25 testes, zero falhas/erros/ignorados na execução aprovada.

AuthenticationApiTest (20): login correto; senha incorreta; usuário inexistente; desabilitado; entrada inválida; campos ausentes; JSON malformado; senha UTF-8 acima de 72 bytes; sem token; token válido; malformado; assinatura diferente; expirado; sem exp; usuário desativado após emissão; role sem permissão (403); 11ª tentativa e X-Forwarded-For forjado (429); CORS permitido; origem rejeitada; erro interno sem vazamento.

JwtServiceTest (2): claims mínimas e duração; chave fraca rejeitada. LoginRateLimiterTest (1): isolamento de IP e reinício da janela. DemoUserInitializerTest (2): hash BCrypt/normalização sem sobrescrever usuário existente e rejeição segura de senha acima do limite.

Usam MVC/Security/BCrypt/JWT reais e repositório simulado, sem H2. Não provam a integração JPA/Flyway/banco. Teste adicional da migration foi realizado diretamente no SQL Server local via sqlcmd, em banco temporário removido após conferir colunas e índices.

Build Angular e Maven clean package aprovados. O agente Mockito é explícito no Surefire; permanece aviso de compartilhamento de classes da JVM, sem falha funcional. Não há suíte automatizada frontend.

## 15. Como executar

1. Defina DB_URL, DB_USERNAME, DB_PASSWORD e JWT_SECRET.
2. Para Compose, defina MSSQL_SA_PASSWORD; execute `docker compose up -d sqlserver` e `docker compose run --rm db-init`.
3. Habilite opcionalmente DEMO_USER_ENABLED e configure e-mail/senha.
4. Em backend: `.\mvnw.cmd spring-boot:run`.
5. Na raiz, outro terminal: `npm ci` e `npm start`.
6. Abra localhost:4200 e faça o roteiro manual do README.

Variáveis PowerShell, URL JDBC e detalhes de TLS local estão documentados no README. Spring não importa .env automaticamente.

## 16. Limitações

- Docker/Compose não executado; Docker não está disponível.
- SQL Server local responde via autenticação Windows, mas não estava escutando TCP 1433. Não foram alteradas configurações globais.
- Integração JDBC/Flyway/JPA, persistência do initializer e login completo pelo navegador permanecem pendentes.
- Alvo Java 21 compilado com JDK 23; não houve runtime Java 21 disponível para testar.
- Token somente em memória, sem persistência entre reloads ou revogação por logout.
- Rate limit local; sem defesa distribuída, CAPTCHA ou bloqueio por conta.
- HTTPS de produção não configurado; origem/URL precisam ser ajustadas na implantação.
- Botões de cadastro/recuperação continuam sem essas funcionalidades.
- Não há slides finais, publicação, commit ou push automático.

## 17. Melhorias futuras

Depois do escopo acadêmico: testes de integração com SQL Server real em ambiente automatizado, testes de UI, usuário SQL de privilégio mínimo com migrations separadas, HTTPS/certificados, gestão/rotação de chaves, observabilidade sem dados sensíveis e estratégia de revogação se o produto exigir. Não estão implementados.
