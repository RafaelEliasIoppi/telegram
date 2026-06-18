# Changelog

## 2026-06-18

- **Novo monitor Gmail — Urgência Renal**: leitura de caixa de entrada via IMAP/SSL (porta 993) usando Google App Password, com filtro de assunto contendo "Notificação de novo e-mail – Urgência Renal". Mensagens encontradas são encaminhadas como alerta para o Telegram. Intervalo padrão: 5 minutos.
- **Painel de controle aprimorado** (`/dashboard`): cartões de estatísticas (alertas hoje, 7 dias, fontes ativas, chats), painel "Fontes de Monitoramento" com status ao vivo de Defesa Civil RS, INMET e Gmail, seção de Ações Rápidas e tabela de alertas recentes.
- **Novas variáveis de ambiente**: `GMAIL_ENABLED`, `GMAIL_IMAP_HOST`, `GMAIL_IMAP_PORT`, `GMAIL_USER`, `GMAIL_APP_PASSWORD`, `GMAIL_SUBJECT_FILTER`, `GMAIL_FIXED_RATE`. Detalhes em `docs/GMAIL_SETUP.md` e `docs/DEPLOYMENT.md`.
- **Documentação**: adicionados `GMAIL_SETUP.md`, `DEPLOYMENT.md`, `UI_GUIDE.md` e este `CHANGELOG.md` em `docs/`.
