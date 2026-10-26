# F02-02 - Catalogo de servicos (UX-04 e UX-07)

Esta etapa implementa apenas a API Java. A composicao Android UX-04/UX-07
e a integracao via Retrofit/Compose pertencem as etapas de interface.

## Endpoints protegidos por Authorization: Bearer accessToken

| Metodo | Rota | Resultado |
| --- | --- | --- |
| GET | /api/v1/services | 200 array incluindo inativos |
| GET | /api/v1/services?activeOnly=true | 200 apenas ativos (dropdown UX-04) |
| GET | /api/v1/services/{id} | 200 ou 404 |
| POST | /api/v1/services | 201, Location e objeto |
| PATCH | /api/v1/services/{id} | 200, objeto atualizado |
| DELETE | /api/v1/services/{id} | 204, desativacao logica |

O barberId vem exclusivamente do JWT validado pelo servidor.
IDs de outro barbeiro retornam 404; nenhum dado de outros barbeiros
pode aparecer na lista. Sem autenticacao: 401.

## DTO JSON

Exemplo POST: {"name":"Corte Degrade","durationMinutes":45,
"defaultPrice":45.00,"isHaircut":true,"active":true,"iconKey":"SCISSORS"}

Resposta: id, name, durationMinutes, defaultPrice (BRL, decimal exato),
isHaircut, active, iconKey, createdAt e updatedAt (UTC ISO-8601).
Valores de iconKey: SCISSORS, BEARD, COMBO, EYEBROW, CHILD, OTHER.
O Android escolhe o icone e o estilo visual associado a chave; a API
nao envia cores nem recursos graficos. Icone omitido: SCISSORS.
Ativo omitido na criacao: true. PATCH aceita campos parciais, mas nunca vazio.

Validacoes: nome nao vazio apos trim (ate 150 caracteres),
durationMinutes 1..480; defaultPrice >= 0, ate 10 digitos inteiros
e 2 casas decimais; flag isHaircut obrigatoria na criacao.
Campos invalidos geram HTTP 400 e mensagem generica.

## Historico e integridade

DELETE e desativacao logica, preservando FK em appointments.
Servicos inativos sao omitidos no dropdown, mas visiveis no catalogo
administrativo (GET sem activeOnly). Podem ser reativados via PATCH.
AgendaSchedulingService ja copia nome e preco para
appointments.service_name_snapshot e appointments.price_snapshot quando
a reserva e criada, portanto mudar os valores no catalogo nao reescreve
atendimentos antigos. F02-04 implementara endpoints HTTP para agendamentos.

## Flyway / deploy

V3__service_visual_key.sql apenas adiciona coluna icon_key com default e CHECK;
V1 e V2 permanecem intocadas. Antes de iniciar a nova API contra Neon,
aplicar V3 com o migrador autorizado e executar flyway validate.
O Spring Boot em runtime continua com spring.flyway.enabled=false.
Este commit nao executa migracao no Neon e nao realiza deploy.
