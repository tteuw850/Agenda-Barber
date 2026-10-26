# F02-03 — Expediente, bloqueios e horários livres (UX-02/04/08/10)

Contrato backend Java/Spring Boot. Todas as rotas requerem JWT Bearer válido.
O ID do barbeiro vem somente do JWT (claim barber_id), nunca do corpo ou URL.
Datas/horas recorrentes são locais ao timezone registrado em barbers.timezone
(padrão America/Sao_Paulo); períodos pontuais e slots usam instantes UTC.

## Rotas

| Método | Endpoint | Finalidade |
|---|---|---|
| GET | /api/v1/schedule/working-hours | Expediente configurado por dia (1=segunda ... 7=domingo) |
| PUT | /api/v1/schedule/working-hours/{weekday} | Substituir expediente de um dia |
| GET | /api/v1/schedule/blocks?date=2030-03-01 | Bloqueios pontuais que tocam a data local |
| POST | /api/v1/schedule/blocks | Bloqueio pontual com motivo e observações |
| DELETE | /api/v1/schedule/blocks/{id} | Remover bloqueio próprio |
| GET | /api/v1/schedule/recurring-blocks | Regras semanais cadastradas |
| POST | /api/v1/schedule/recurring-blocks | Bloqueio recorrente por dia da semana |
| DELETE | /api/v1/schedule/recurring-blocks/{id} | Remover regra própria |
| GET | /api/v1/schedule/available-slots?date=2030-03-01&serviceId=UUID | Slots de 15 min, duração do serviço |

### JSON de entrada

PUT working-hours/5:
{"enabled":true,"opensAt":"08:00","closesAt":"18:00"}

POST blocks:
{"startsAt":"2030-03-01T15:00:00Z","endsAt":"2030-03-01T16:00:00Z",
 "reason":"Almoço","notes":"Retorno às 13h locais"}

POST recurring-blocks:
{"weekday":5,"startsAt":"12:00","endsAt":"13:00",
 "validFrom":"2030-03-01","validUntil":"2030-05-31",
 "reason":"Intervalo de almoço","notes":"Pausa semanal"}

### Semântica e segurança

- GET working-hours retorna linhas existentes, em ordem; dias sem registro
  são considerados fechados. PUT substitui as linhas daquele dia por uma
  janela, aberta ou fechada. Para pausas dentro do expediente, usar bloqueio
  recorrente; não tentar configurar dois turnos pelo PUT.
- Bloqueio pontual dura no máximo 31 dias; bloqueio recorrente semanal
  possui janela local no mesmo dia e vigência máxima de 180 dias.
- Não aceitar bloqueio que colida com atendimentos ativos, outros bloqueios
  ou regras recorrentes. PUT expediente não fecha um horário com reserva ativa.
  Conflitos retornam 409. Intervalos adjacentes não conflitam.
- Concorrência: operações de escrita e agendamento interno bloqueiam
  SELECT ... FROM barbers FOR UPDATE antes de validar e persistir.
  AgendaSchedulingService agora verifica regras recorrentes além de
  blocked_periods. A reserva revalida dentro de transação; slots livres
  são apenas uma indicação de disponibilidade, não uma reserva.
- Endpoint available-slots devolve startsAt/endsAt em UTC e localStart
  em horário do barbeiro. Slots são propostos a cada 15 minutos, têm
  a duração atual do serviço e ignoram dias fechados/ocupados/bloqueados.
  Horários locais inexistentes/ambíguos devido a DST não são oferecidos.
- 400 para intervalo, dia ou corpo inválido; 401 JWT ausente/inválido;
  404 para ID fora do barbeiro ou inexistente; 409 para colisões.
- Regras recorrentes são armazenadas; o GET blocks retorna apenas os
  bloqueios pontuais. A UI UX-08 combina GET blocks e recurring-blocks.

### Migração e dependências

Flyway V5 adiciona notes a blocked_periods e a tabela recurring_blocks;
V1-V4 permanecem inalteradas. Aplicar V5 via migrador autorizado
ANTES do deploy da nova versão no Neon; o runtime usa
spring.flyway.enabled=false. A alteração no GitHub não executa migração
nem inicia deploy de produção. Verificar backup/snapshot e flyway validate.

UX-08/10 Compose/Retrofit e interface de agenda UX-02/04 permanecem
nas tarefas Android. F02-04 implementará a API HTTP de agendamentos,
utilizando essas regras. Nenhum APK funcional de agenda é criado por este PR.
