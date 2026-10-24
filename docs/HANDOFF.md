# Agenda Barber - Handoff

APK exclusivo para barbeiro. Duas abas: Agenda e Resultados.
Stack: Java 21 Spring Boot API + Kotlin Jetpack Compose Android + Neon PostgreSQL.
Escopo e tasks completos: https://trello.com/b/p6yR1n5x/appbarbearia

## Funcionalidades do MVP
Agenda dia/semana; criar, editar, remarcar, cancelar e concluir atendimentos; bloquear horarios; cadastrar clientes e servicos; historico.
Resultados: cortes concluidos, faturamento bruto realizado, despesas registradas, lucro liquido estimado, ticket medio, ranking e grafico por periodo.
Sem app do cliente, reservas publicas, PIX ou WhatsApp automatico. Um barbeiro no MVP.

## Arquitetura
APK Kotlin Compose/MVVM/Retrofit/Room -> HTTPS JWT -> API Java Spring Boot -> JDBC TLS -> Neon PostgreSQL.
Pacotes planejados backend: auth, clients, services, appointments, blocked-time, expenses, reports. Flyway para schema; DTOs e validacao.
Nunca incluir senha do banco no APK ou Git. Hospedagem JVM HTTPS a definir e validar gratuitamente.

## Regras de negocio
Status: AGENDADO, EM_ATENDIMENTO, CONCLUIDO, CANCELADO, NAO_COMPARECEU.
Agendamentos ativos sem conflito: constraint de exclusao PostgreSQL e validacao transacional.
Receita considera atendimentos concluidos e pagos; lucro estimado = receita - despesas lancadas.
Armazenar horarios UTC e mostrar America/Sao_Paulo; usar BigDecimal para BRL; preservar snapshot de preco e tipo de servico.
