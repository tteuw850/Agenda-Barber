# F04-01   HikariCP e resiliencia do Neon

## Escopo deste ajuste
Branch local feat/f04-01-neon-hikari. Nenhum deploy, merge ou alteracao no Neon.
O perfil neon em backend/src/main/resources/application.yml define:
- minimumIdle=0, maximumPoolSize=3, idleTimeout=60000 ms, keepaliveTime=0.
- connectionTimeout=20000 ms e validationTimeout=5000 ms.
- pgJDBC connectTimeout=10 s, loginTimeout=15 s e socketTimeout=60 s.
- logServerErrorDetail=false para reduzir detalhes SQL sensiveis em excecoes.
- Hikari pool-name=AgendaBarberNeonPool e registerMbeans=true (JMX local).

## TLS, seguranca e monitoramento
NEON_JDBC_URL deve continuar vindo de variavel de ambiente no servidor,
apontando para DEVELOPMENT e incluindo sslmode=verify-full com CA confiavel.
Nunca versionar senhas, arquivos .env, chaves RSA ou credenciais do Neon.
Nao expor portas de JMX nem actuator/metrics na internet.
Somente health e exposto na API. management.health.db.enabled=false impede
que healthchecks facam consultas ao Neon.
Nao configurar cron SQL, requests de aquecimento ao banco nem WorkManager
periodico. O monitoramento HTTP da API e independente do compute Neon.

## Validacao de codigo
- No worktree: cd backend && mvn -B -ntp verify.
- NeonHikariConfigurationTests valida propriedade por propriedade sem conectar.
- Verificar git diff para garantir ausencia de segredos e mudancas de perfil default.
- Conferir logs da API sem habilitar DEBUG de HikariConfig ou expor URLs JDBC.

## Ensaio real a realizar em DEV, antes de concluir o cartao
1. Registrar horario UTC, status do compute Neon DEV e dados de baseline.
2. Realizar GET autenticado somente-leitura e medir duracao da resposta.
3. Aguardar pelo menos 6 minutos sem consultas de outros clientes ao Neon.
   Verificar no console se o compute efetivamente suspendeu.
4. Fazer outro GET autenticado de leitura e registrar HTTP, duracao,
   eventuais erros transitarios e sucesso de recuperacao manual.
5. Capturar metrica de conexoes e compute-time do Neon DEV e CPU/RAM Docker.
6. Confirmar que healthcheck HTTP da AWS nao reativa o banco.
7. Em caso de falha, registrar somente SQLState e erro sanitizado.

O teste deve ser executado somente no ambiente development e nunca
escrever dados com POST/PATCH/DELETE para provocar wake-up.
Nao prometer suspend se houver outros clientes ainda conectados.
Retries automaticos e 503 estruturado pertencem a F04-02.
Idempotencia de escritas pertence a F04-03.

Referencias:
https://jdbc.postgresql.org/documentation/use/
https://neon.com/docs/manage/endpoints
