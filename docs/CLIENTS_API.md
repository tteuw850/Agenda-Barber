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
| DELETE | /api/v1/clients/{id} | Desativa com confirmação na UI, preserva histórico e responde 204; retorna 409 se houver agendamentos abertos ou dívida |

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
Na versão 1.0.0, a API primeiro bloqueia a linha do cliente por `barber_id`, consulta agendamentos com status `SCHEDULED` ou `IN_PROGRESS` e o saldo do livro de dívidas. Com qualquer agendamento aberto ou saldo devedor positivo, responde **409 Conflict**; só então define `active=false`. O cliente deixa de aparecer nas listas, mas atendimentos e registros financeiros não são fisicamente excluídos. A tela Android exige uma segunda confirmação. Exclusão de cliente inexistente/inativo ou pertencente a outro barbeiro responde 404.

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

## Integração Android na v1.0.0

As telas Compose de lista, busca, favoritos, detalhes, inclusão e remoção foram integradas à API. A interface informa falha de rede, erro de autenticação e bloqueio de exclusão. Imagens de clientes reais e uploads de foto para storage gerenciado são evoluções separadas; o `photoUrl` é somente uma referência HTTPS. Veja [galeria](SCREENS_GALLERY.md) e [README](../README.md).
