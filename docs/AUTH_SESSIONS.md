# F01-05B — Contrato e implantação

## API e segurança

- POST /api/v1/auth/login: JSON com email e password.
- POST /api/v1/auth/refresh: JSON com refreshToken; dispensado bearer.
- POST /api/v1/auth/logout: exige Authorization Bearer, retorna HTTP 204.
- GET /api/v1/auth/me: exige JWT válido.
- Login e refresh retornam accessToken, refreshToken, tokenType, expiresInSeconds (900) e refreshExpiresInSeconds (1209600).
- JWT RS256 vence em 15 minutos. Refresh token opaco: 256 bits aleatórios, Base64URL, 14 dias.
- Banco armazena somente SHA-256 do refresh token; nunca o token bruto.
- Refresh é de uso único. Reuso de token já consumido revoga a família daquela sessão e invalida seu JWT.
- Logout revoga somente a sessão atual. Conta inativa ou credential_version alterada perde acesso.
- Nunca registrar corpo de login/refresh, token, hash, senha ou dados pessoais em logs.

## Integração Android

Guardar tokens protegidos pelo Android Keystore, nunca no Room em texto puro, logs ou analytics.
Trocar o par de tokens de forma atômica depois de refresh bem-sucedido.
Não tentar refresh novamente automaticamente depois de timeout ambíguo: a primeira chamada
pode ter sido aplicada e a repetição pode disparar a proteção contra replay. Solicitar novo login.
Ao receber 401, limpar estado autenticado e oferecer reautenticação.
Tokens JWT antigos, sem os novos claims session_id e credential_version, exigirão novo login.
Nenhuma credencial do Neon pertence ao APK.

## Sequência obrigatória para implantar em Neon

A integração na main NÃO aplica migração nem faz deploy.

1. Confirmar ambiente alvo, backup/snapshot recuperável e janela de manutenção.
2. Migrar utilizando role separada com DDL e segredos de ambiente fora do Git.
   O runtime tem spring.flyway.enabled=false, então NÃO migra automaticamente.
3. Verificar que V1 já foi aplicada; revisar V2__refresh_sessions.sql e executar
   V2 uma única vez pelo mecanismo autorizado de Flyway. Nunca editar V1.
4. Rodar Flyway validate e verificar flyway_schema_history com V1 e V2 success=true.
   Confirmar users.credential_version e a tabela auth_refresh_tokens com índices.
5. Somente após a V2, publicar a nova API com JWT issuer/audience/chaves corretos,
   HTTPS e usuário runtime sem permissão de DDL.
6. Testar login, refresh, replay, logout, invalidação JWT, 401, falha de conexão e
   reativação do Neon em ambiente de testes antes de liberar para clientes.

A migração V2 é aditiva: o backend F01-05A funciona com V2 aplicada. Se necessário,
reverter apenas o binário da API para F01-05A e manter V2 intacta, sem DROP ou rollback destrutivo.
Caso o endpoint refresh tenha timeout com commit desconhecido, não repetir cegamente.

## Aceite do PR

- Maven verify + Checkstyle + PostgreSQL 17 Testcontainers sem falhas.
- GitHub Actions de backend e Android aprovados.
- Proteger sessão e separar autorização por barber_id; verificar inativação e credential_version.
- Revisar diff e arquivos versionados quanto a segredos e modificações fora do escopo.
- Merge do código não é autorização para executar V2 no Neon de produção.
