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

## Provisionamento da conta solicitada — EXCLUSIVAMENTE Neon development
A solicitação é para associar o username `ebinho` à conta atual do
barbeiro, preservando os IDs, clientes, agenda, serviços e despesas.
É necessário configurar a senha solicitada pelo usuário, usando
**somente o hash BCrypt (custo 12)**, e nunca a senha bruta em SQL,
migrações, logs, prints, workflows, commits ou no Trello.

**NÃO executar antes de:** (a) CI Java+Android passar, (b) integrar
a PR com V8, (c) executar e validar o deploy DEV e a migração V8,
(d) confirmar no Neon que existe exatamente um usuário BARBER e
que esse usuário é o pretendido.

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
