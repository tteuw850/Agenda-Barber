# F02-04 — Android conectado à API (UX-02/03/04)

A navegação em Jetpack Compose mantém a barra Agenda | Clientes | + |
Estatísticas | Mais. O escopo F02-04 implementa Agenda, Detalhe/Histórico
e Novo Agendamento. As outras abas exibem aviso explícito de etapa futura.

## Fluxos Android
- Login email/senha contra POST /api/v1/auth/login, com JWT mantido
  somente em memória. URL base precisa ser HTTPS (informada na entrada).
- Agenda por dia/semana/mês, filtros por status, busca por data,
  atualização manual; cartões exibem cliente, serviço, duração, hora
  convertida para o fuso do aparelho, preço BRL e status colorido.
- Detalhe GET /appointments/{id}, histórico GET /{id}/history, iniciar,
  concluir e cancelar com resposta real do servidor, editar observações.
- Novo agendamento: consulta serviços ativos, busca cliente por nome/
  telefone, cadastro simples com nome/telefone, slots GET available-slots,
  seleção de preço e observações; POST com Idempotency-Key UUID.
- Erros 400/401/404/409/503 visíveis na interface. Nenhum "salvo com
  sucesso" sem HTTP 2xx. Em timeout de escrita, a mesma chave é
  reaproveitada se o formulário não mudou. Não há replay automático
  de escritas.

## Para testar em dispositivo
1. Subir a API backend com profile neon, migrations V1–V6 já aplicadas.
2. Publicar endpoint HTTPS válido e informar URL na tela inicial.
3. Entrar com conta de barbeiro existente e testar CRUD real.
4. Sem uma API acessível, a UI exibe erro de rede, não usa dados falsos.

## Limitações do escopo
- Nenhum servidor/API foi implantado por estes arquivos.
- Navegação dos demais módulos, edição de hora/cliente/serviço do
  agendamento, persistência offline com Room, fotos e UX pixel-perfect
  dependem de etapas Android dedicadas. Este pacote cobre apenas o
  fluxo operacional mínimo de F02-04.
- Nunca incluir tokens, URLs com segredo ou credenciais no APK/Git.
- A migração V6 não deve ser aplicada à produção vazia sem V1–V5.
