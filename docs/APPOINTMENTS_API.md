# F02-04 — API de agendamentos (UX-02, UX-03, UX-04)

Backend Java 21 / Spring Boot. Todas as rotas exigem JWT Bearer válido.
O backend deriva barber_id do JWT; nunca aceita o identificador vindo do cliente.

## Endpoints

| Método | Rota | Ação |
|---|---|---|
| GET | /api/v1/appointments?date=2030-03-01&view=day&status=all | Listar agenda |
| GET | /api/v1/appointments/{id} | Detalhe |
| GET | /api/v1/appointments/{id}/history | Histórico dos eventos |
| POST | /api/v1/appointments | Criar (Idempotency-Key obrigatório) |
| PATCH | /api/v1/appointments/{id} | Editar SCHEDULED |
| POST | /api/v1/appointments/{id}/start | Iniciar |
| POST | /api/v1/appointments/{id}/complete | Concluir |
| POST | /api/v1/appointments/{id}/cancel | Cancelar |
| GET | /api/v1/schedule/available-slots?date=...&serviceId=UUID | Slots UX-04 (F02-03) |

view: day (data local), week (segunda/domingo) ou month.
status: all, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED ou NO_SHOW.
limit 1..500 (padrão 100); offset 0..10000. Filtro usa timezone do barbeiro.

Resposta AppointmentView: id, clientId, clientName, clientPhone, serviceId,
serviceName, durationMinutes, price, startsAt, endsAt, status, notes,
completedAt, version, createdAt, updatedAt. Valores monetários são BigDecimal;
retornos temporais usam UTC. Cores de status pertencem ao Android.

## Criar agendamento

Header: Idempotency-Key: 0e7f9a22-2472-40d1-a17f-f624e517c808

POST body:
{"clientId":"UUID","serviceId":"UUID","startsAt":"2030-03-01T13:00:00Z",
 "price":47.50,"notes":"Cliente pediu corte curto"}

clientId, serviceId e startsAt obrigatórios. Price/notes opcionais,
preço mínimo zero, duas casas decimais. Duração deriva do serviço ativo,
sem confiar no aplicativo. Regras de expediente, bloqueios únicos e
recorrentes e isolamento por barbeiro são rechecadas na transação.
Preço e nome do serviço são copiados para snapshot na criação.

Idempotency-Key (8–128 caracteres permitidos): hash SHA-256 do payload
e id persistidos na mesma transação do agendamento. Mesma chave e
payload retornam o mesmo recurso; chave com outro payload retorna 409.
Duas tentativas simultâneas com mesma chave são serializadas por lock
de usuário. Repetições após timeout ambíguo devem usar a mesma chave.
Resposta de sucesso é emitida somente depois de commit PostgreSQL.

## Editar, iniciar, concluir, cancelar

PATCH body parcial: {"startsAt":"2030-03-01T14:00:00Z",
"price":55.50,"notes":"Cliente pediu outro horário"}

Campos permitidos: clientId, serviceId, startsAt, price, notes.
Campos ausentes mantêm valores. notes=null limpa observações.
Preço é preservado ao editar sem trocar o serviço; se trocar serviço,
passa ao novo preço padrão salvo quando informado explicitamente.
Só permite editar status SCHEDULED, sob lock do barbeiro e revalidação.
Eventos salvos em appointment_events: CREATED, EDITED, STARTED,
COMPLETED, CANCELLED. Respostas de status repetidas não duplicam eventos.

Transições válidas: SCHEDULED -> IN_PROGRESS -> COMPLETED;
SCHEDULED/IN_PROGRESS -> CANCELLED. COMPLETED define completedAt.
Outra transição retorna 409.

## HTTP e indisponibilidade

400 entrada/parâmetro inválido; 401 JWT ausente; 404 ID não encontrado
ou fora do barbeiro; 409 colisão de horário/estado/idempotência;
503 banco/transação temporariamente indisponível (Retry-After: 5).
Não retornar sucesso antes do commit, não registrar segredos em logs.

## Deploy e aceite

V6__appointment_events.sql adiciona a tabela appointment_events e
unicidade composta appointments(barber_id,id), sem alterar V1–V5.
Aplicar V6 no Neon pelo migrador autorizado ANTES de subir a nova API.
Flyway está desabilitado no runtime. Verificar backup e flyway validate.
A PR não faz deploy nem migração Neon.

O backend prepara UX-02/03/04, mas a integração Retrofit + telas Compose
continua pendente nas tarefas Android. Não confundir teste do job Android
com implementação visual dos mocks.
