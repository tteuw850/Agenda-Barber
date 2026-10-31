# F05 — Entrar com nome de usuário (Android e API DEV)

## UX
O aplicativo mostra apenas **Nome de usuário** e **Senha**.
A URL da API não é solicitada nem editável na tela de login.
O endereço HTTPS da EC2 está no `BuildConfig.API_BASE_URL`, definido
em `android-app/app/build.gradle.kts` (não é segredo).
**Atenção:** o hostname sslip.io atual contém o IPv4 da EC2 e poderá
deixar de resolver se a instância mudar de IP; não criar novos APKs
sem verificar o endpoint atual.

## Backend
A migração Flyway `V8__barber_usernames.sql` adiciona
`users.username` opcional, formato limitado e único.
O login recebe JSON:
```json
{"username": "seu_usuario", "password": "<senha digitada no aparelho>"}
```
O backend continua aceitando o campo legado `email` e login por e-mail
somente para compatibilidade com APKs já instalados. A senha é comparada
com o hash BCrypt existente na tabela `users`; JWT, refresh, revogação
e limites de tentativas permanecem habilitados. Nunca há senha ou token
predefinido no APK.

## Situação do Neon DEV em 31/10/2026
- A coluna opcional `username` e as restrições/índice foram aplicados
  por role migradora com SQL equivalente à V8 **somente no branch development**.
- O username `ebinho` foi associado ao único barbeiro existente (1 linha).
- A senha ainda NÃO foi alterada: a operação administrativa de atualização
  de hash foi bloqueada pela ferramenta. Não considerar as credenciais novas
  ativas até provisionamento seguro e verificação HTTP 200.
- `flyway_schema_history` ainda está na versão 7. A V8 foi deliberadamente
  escrita de modo idempotente para que uma execução posterior do Flyway
  marque a migração oficialmente sem recriar coluna/índice/constraint.
  Não marcar a V8 no histórico manualmente.
- Nenhum acesso ao Neon production e nenhum deploy foi realizado nesta etapa.

## Provisionamento da conta solicitada — EXCLUSIVAMENTE Neon development
A solicitação é para associar o username `ebinho` à conta atual do
barbeiro, preservando os IDs, clientes, agenda, serviços e despesas.

**Atenção operacional:** o perfil `neon` usa `spring.flyway.enabled=false`.
O workflow de CD AWS troca a imagem da API mas **não aplica Flyway V8**.
Antes do deploy, aplicar V8 de modo controlado com o migrador do Neon
DEVELOPMENT e conferir `flyway_schema_history` versão 8. Não presumir
que a API consegue criar ou alterar tabelas usando a role runtime.
É necessário configurar a senha solicitada pelo usuário, usando
**somente o hash BCrypt (custo 12)**, e nunca a senha bruta em SQL,
migrações, logs, prints, workflows, commits ou no Trello.

**NÃO executar antes de:** (a) CI Java+Android passar, (b) integrar
a PR com V8, (c) aplicar a migracao V8 **separadamente, SOMENTE no Neon
 development**, usando credencial migradora e Flyway para registrar
 checksum/historico, (d) executar e validar o deploy AWS DEV,
 (e) confirmar que existe exatamente um usuario BARBER e
 que esse usuario e o pretendido.

Atualização administrativa esperada (somente com um hash produzido
localmente e fornecido como parâmetro seguro ao SQL):
```sql
BEGIN;
UPDATE users
SET username = 'ebinho',
    password_hash = '<HASH_BCRYPT_PRODUZIDO_FORA_DO_BANCO>',
    credential_version = credential_version + 1,
    updated_at = now()
WHERE id = '<UUID_USUARIO_VALIDADO>'
  AND role = 'BARBER'
  AND active = true
  AND (username IS NULL OR username = 'ebinho');
COMMIT;
```

Não colar essa SQL com placeholders sem substituir. Confirmar exatamente
uma linha atualizada; se 0 ou mais de 1, cancelar e investigar. O incremento
de `credential_version` invalida tokens existentes (força novo login).
O Neon **production** jamais deve receber essa atualização.

## Validação
- Novo APK: só dois campos, sem opção de URL.
- Login com username e senha: HTTP 200, token válido, mesmo `barberId`.
- Senha incorreta/usuário inexistente: HTTP 401 genérico.
- Nome de usuário duplicado: constraint 23505.
- APK antigo: ainda pode autenticar por e-mail.
- Confirmar que clientes, agendamentos e serviços existentes continuam
  acessíveis pelo mesmo `barber_id`.
- Testar o aparelho e só então preparar release assinada.

## Limites operacionais
Não incluir senha fixa no código ou nos testes. CD da AWS permanece
manual, `DEPLOY_AWS_DEV`; merge sozinho não aplica migração na EC2.
Não alterar o Neon production nem o Render. O CodeBridge pode estar
desconectado; sem ele, testes de dispositivo exigem ação manual.
