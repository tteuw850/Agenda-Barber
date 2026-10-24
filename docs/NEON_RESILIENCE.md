# Neon: suspend/resume e plano de resiliencia

## Risco
Neon Free suspende compute apos cerca de 5 minutos sem atividade. Primeira conexao reacorda banco; pode ter atraso ou erro transitorio. Nao confundir com perda de dados nem com suspensao do host da API Java.

## API Java / Spring Boot
- HikariCP com pool elástico inicial: minimumIdle=0, maximumPoolSize=3, idleTimeout=60000, connectionTimeout=20000, validationTimeout=5000, keepaliveTime=0. Ajustar por testes e quota.
- TLS JDBC; credenciais via ambiente somente servidor; nao usar pings/cron/healthchecks SQL frequentes para manter DB acordado.
- Detectar SQLState classe 08/SQLTransientConnectionException. Repetir apenas GETs idempotentes, ate duas novas tentativas, backoff exponencial e jitter, com timeout total.
- Ao esgotar: HTTP 503 DB_TEMPORARILY_UNAVAILABLE com Retry-After e mensagem amigavel.
- Para mutacoes: Idempotency-Key UUID e registro atomico da operacao. Em timeout apos commit, consultar status com a mesma chave; nunca repetir cegamente.
- Constraint EXCLUDE btree_gist + tstzrange para evitar dois atendimentos simultaneos para barbeiro/status ativo. Responder 409 no conflito.

## Android Kotlin / Room
- Exibir agenda ja sincronizada imediatamente do Room e atualizar em rede; mostrar data de ultima sincronizacao.
- Modo offline MVP somente leitura: nao confirmar criar/editar/cancelar sem sucesso real do servidor.
- Se erro temporario, apresentar carregamento/retry manual e manter cache identificado como possivelmente desatualizado.
- Sem WorkManager fazendo pings continuos ao Neon.

## Testes de aceitacao
1. Deixar Neon sem trafego por pelo menos seis minutos e medir primeira leitura/reconexao.
2. Simular falhas conexao e 503; validar retry e erro claro.
3. Reenviar POST com Idempotency-Key: apenas um registro.
4. Reservar mesmo horario em concorrencia: 1 sucesso e 1 HTTP 409.
5. Modo aviao: mostrar cache com timestamp; nao exibir sucesso de escrita falso.
6. Validar cota Neon, backup e recuperacao.

Docs: https://neon.com/docs/manage/endpoints/
