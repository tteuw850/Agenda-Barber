# F02-01 — Catálogo de clientes: contrato API UX-04/05/06

Implementação Java 21 / Spring Boot. Endpoints exigem JWT Bearer válido.
O servidor extrai barber_id do JWT e restringe TODAS as consultas/alterações
a esse barbeiro. IDs de outro barbeiro retornam 404 (sem vazamento de existência).

## Endpoints

| Método | Rota | Efeito |
| --- | --- | --- |
| GET | /api/v1/clients?search=&filter=all&limit=50&offset=0 | Lista paginada de clientes ativos |
| GET | /api/v1/clients?filter=recent | Clientes com atendimento concluído, por data decrescente |
| GET | /api/v1/clients?filter=favorites | Apenas clientes marcados como favoritos |
| GET | /api/v1/clients/{id} | Detalhes do cliente, ou 404 |
| POST | /api/v1/clients | Cria cliente, responde 201 + Location |
| PATCH | /api/v1/clients/{id} | Edita campos enviados, responde 200 |
| DELETE | /api/v1/clients/{id} | Desativa sem excluir dados históricos, responde 204 |

GET: search faz busca parcial por nome, sem diferenciar maiúsculas/minúsculas,
ou por dígitos de telefone. Filtros aceitos: all (padrão), recent e favorites.
limit entre 1 e 100, offset entre 0 e 10.000; limite padrão 50.
Clientes sem atendimento aparecem em all e favorites, mas não em recent.

POST JSON:
{"name":"Ana Silva","phone":"+55 (31) 99999-1234",
 "email":"ana@example.com","birthDate":"2000-06-15",
 "notes":"Preferência por corte curto","favorite":true,
 "photoUrl":"https://imagens.exemplo.test/avatar/ana.jpg"}

name: obrigatório, sem espaços em branco, até 150 caracteres.
phone: opcional, aceita pontuação, 10/11 dígitos nacionais e +55; API
persiste apenas os 10/11 dígitos. Duplicatas de telefone entre clientes
do mesmo barbeiro retornam HTTP 409; barbeiros diferentes podem usar
o mesmo telefone. A verificação vale também para clientes desativados.
email: opcional, formato validado, máximo 254, normalizado em minúsculas.
birthDate: opcional, ISO-8601, não pode ser futura.
notes: opcional, máximo 4.000 caracteres.
favorite: opcional, false por padrão. PATCH favorite true/false altera estrela.
photoUrl: opcional, referência HTTPS até 512 caracteres. Foto NÃO é carregada
pelo backend; upload binário e armazenamento de imagens são trabalhos separados.

Resposta ClientView:
id, name, phone, email, birthDate, notes, favorite, photoUrl,
active, completedAppointments, lastAppointmentAt, createdAt, updatedAt.

completedAppointments considera SOMENTE status COMPLETED.
lastAppointmentAt é o starts_at mais recente desses atendimentos, em UTC.
Campos opcionais sem valor são null. As telas podem formatar localmente
telefone nacional e data de aniversário e representar foto ausente por avatar.

PATCH: objeto parcial. Campos ausentes permanecem; null apaga phone,
email, birthDate, notes e photoUrl. name ou favorite em null, objeto
vazio ou campos desconhecidos devolvem 400. DELETE é soft delete:
inativo deixa de aparecer em listagem e GET, mas appointments mantém FK.
Não há exclusão física de clientes nesta fase.

Conexão UX-04: GET com search e selecionar id do cliente ao agendar;
UX-05: POST e retornar id após salvar; UX-06: filtros all/recent/favorites,
contagem, última visita, estrela via PATCH.

## Flyway

V4__client_catalog_details.sql adiciona photo_url e active e índices.
NÃO altera V1, V2 ou V3. Antes da migração em banco com dados, procurar
duplicações de (barber_id, phone) não nulos e resolver antes da criação
do índice unique; conferir formatos legados de telefones.
spring.flyway.enabled=false no runtime: executar V4 com migrador
autorizado e validar ANTES do deploy da API, sem secrets no Git/APK.
Este PR não aplica migração no Neon.

## Fora do escopo deste backend

A criação visual das telas UX-05/UX-06 em Jetpack Compose, Retrofit,
cache Room, upload real de fotos e interação com agendamento por HTTP
são tarefas de UI F02-08/F02-10 e fluxo F02-04/F02-07. Não considerar
integração visual ou deploy como concluídos apenas pelo CI Java/Android.
