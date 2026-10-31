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

## Evidencias coletadas em 31/10/2026

- Docker Desktop 29.6.2 iniciado no Windows e reconhecido pelo Testcontainers 2.0.5.
- `mvn -B -ntp verify`: BUILD SUCCESS, 67 testes (66 passaram, 1 ignorado, 0 falhas, 0 erros). O unico ignorado foi NeonConnectionSmokeTest, condicionado a SPRING_PROFILES_ACTIVE=neon.
- Os testes de integracao criaram PostgreSQL 17 temporario em Docker e aplicaram/validaram as sete migrations Flyway exclusivamente nesses bancos de teste.
- Compute Neon DEVELOPMENT `ep-morning-boat-b6b8phdw` observado em `idle`, com `suspended_at=2026-10-31T05:35:31Z`.
- Uma unica consulta SELECT sem mutacoes na branch DEVELOPMENT, pelo conector Neon, retornou 1 e usuario `agenda_app_dev` em aproximadamente 3082 ms. Logo apos, o endpoint passou para `active` (started_at=2026-10-31T05:46:47Z).
- A sessao de SQL do conector retornou pg_stat_ssl=false; isso NAO testa TLS do driver JDBC da aplicacao (sslmode=verify-full). Validar TLS no caminho real da API separadamente.
- Nao houve alteracao no banco Production, no container AWS, no Render, nem merge/push/deploy.

### Pendente para encerrar F04-01
- Executar GET autenticado atraves da API Java com o novo HikariCP apos suspensao real do compute e registrar tempo/HTTP/logs sem expor segredos.
- Conferir metricas JMX do pool na aplicacao com o novo build, sem abrir porta JMX externa.
- Preparar revisao por PR e autorizacao de deploy DEV separadamente.

## Validacao adicional JMX / pool (31-10-2026)
- Adicionado NeonHikariRuntimeIntegrationTests com PostgreSQL 17 efemero.
- Binder aplica as propriedades Hikari do perfil neon e confirma nome, minimo=0, maximo=3, timeout do driver e keepalive=0.
- Confirma MBean JMX local, conexao SQL SELECT 1, total de conexoes limitado e devolucao da conexao ao pool.
- Nome JMX exclusivo dentro do teste evita colisao com o pool da suite de autenticacao.
- mvn -B -ntp verify: BUILD SUCCESS; 68 testes no total, 67 aprovados, 1 ignorado, 0 falhas ou erros.
- Testes de autenticacao ja exercitam GET autenticado com configuracao neon em banco isolado.
- Pendente: validar GET autenticado no Neon DEVELOPMENT apos idle com essa versao do backend, sem deploy nao autorizado.

## Resiliencia de conexao interrompida (31/10/2026)
- Em PostgreSQL 17 descartavel do Testcontainers, o teste identificou o PID da sessao ociosa do Hikari e executou pg_terminate_backend nesse PID.
- A operacao sobre a sessao interrompida pode falhar com SQLSTATE 57P01. O Hikari marca a conexao como quebrada e a descarta; nao faz replay de comandos SQL.
- Uma nova tentativa de leitura SELECT 1, usando pool.getConnection() novamente, obteve conexao funcional. O pool continuou com no maximo 3 conexoes e 0 emprestadas.
- Esse teste simula perda de sessao, nao prova automaticamente recuperacao de chamada HTTP nem suspend/resume real Neon. O retry automatico limitado de GET pertence a F04-02.
- Execucao final: mvn -B -ntp verify = BUILD SUCCESS; 68 testes, 67 aprovados, 1 ignorado, 0 falhas, 0 erros; Checkstyle 0 violacoes.
- Tentativa de smoke JDBC direto com credenciais foi bloqueada pelas configuracoes da ferramenta. Nao contornar, nao transportar segredos e nao alegar teste HTTP contra o Neon DEV.
- Proximo aceite: GET autenticado contra Neon DEVELOPMENT com o novo backend em ambiente autorizado; conferir certificado TLS e logs do primeiro request apos idle.
