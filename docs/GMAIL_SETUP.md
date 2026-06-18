# Configuração do Monitor Gmail — Urgência Renal

Este guia descreve como ativar o monitor de Gmail que observa uma caixa de entrada via IMAP e encaminha como alerta para o Telegram todo e-mail cujo assunto contenha **"Notificação de novo e-mail – Urgência Renal"**.

> A conexão é feita por **IMAP sobre SSL** (porta 993) usando uma **Senha de App do Google** — **não** OAuth2. Por isso é obrigatório habilitar a Verificação em duas etapas antes.

---

## 1. Pré-requisitos

- Conta Google que recebe os e-mails de Urgência Renal.
- Acesso administrativo à conta (para habilitar 2FA e gerar a App Password).
- O bot já configurado e em execução (Telegram token + chat IDs).

---

## 2. Habilitar a Verificação em duas etapas (2FA)

A Senha de App só pode ser gerada em contas com 2FA ativo.

1. Acesse [https://myaccount.google.com/security](https://myaccount.google.com/security).
2. Em **Como você faz login no Google**, abra **Verificação em duas etapas**.
3. Siga o assistente (telefone ou app autenticador).

---

## 3. Gerar a Senha de App

1. Acesse [https://myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords).
2. Em **Nome do app**, digite por exemplo `Telegram Bot Urgência Renal` e clique em **Criar**.
3. O Google exibirá uma senha de **16 caracteres** (algo como `abcd efgh ijkl mnop`).
4. **Copie e remova os espaços** — a senha vai para a variável `GMAIL_APP_PASSWORD` sem espaços.

> A senha é exibida apenas uma vez. Se perder, gere outra.

---

## 4. Configurar as variáveis de ambiente

### Opção A — arquivo `.env` (modo local)

Crie ou edite `/workspaces/telegram/teste/.env` (já lido automaticamente pelo `application.properties`):

```dotenv
GMAIL_ENABLED=true
GMAIL_IMAP_HOST=imap.gmail.com
GMAIL_IMAP_PORT=993
GMAIL_USER=rafaelioppi@gmail.com
GMAIL_APP_PASSWORD=abcdefghijklmnop
GMAIL_SUBJECT_FILTER=Notificação de novo e-mail – Urgência Renal
GMAIL_FIXED_RATE=300000
```

### Opção B — `docker-compose.yml`

Acrescente as variáveis ao bloco `environment:` do serviço `app`:

```yaml
environment:
  - GMAIL_ENABLED=true
  - GMAIL_USER=rafaelioppi@gmail.com
  - GMAIL_APP_PASSWORD=abcdefghijklmnop
  - GMAIL_SUBJECT_FILTER=Notificação de novo e-mail – Urgência Renal
  - GMAIL_FIXED_RATE=300000
```

### Tabela de variáveis

| Variável | Obrigatório? | Padrão | Descrição |
|---|---|---|---|
| `GMAIL_ENABLED` | Não | `true` | Liga/desliga o monitor sem remover credenciais. |
| `GMAIL_IMAP_HOST` | Não | `imap.gmail.com` | Host IMAP. Só altere para contas Workspace com proxy. |
| `GMAIL_IMAP_PORT` | Não | `993` | Porta IMAP SSL. |
| `GMAIL_USER` | **Sim** | — | E-mail completo a ser monitorado. |
| `GMAIL_APP_PASSWORD` | **Sim** | — | Senha de App de 16 caracteres, **sem espaços**. |
| `GMAIL_SUBJECT_FILTER` | Não | `Notificação de novo e-mail – Urgência Renal` | Substring exigida no assunto (case-insensitive). Atenção aos acentos. |
| `GMAIL_FIXED_RATE` | Não | `300000` | Intervalo de varredura em milissegundos (300000 = 5 min). |

---

## 5. Reiniciar a aplicação

Local:

```bash
./start.sh
```

Docker:

```bash
docker compose up -d --build
```

---

## 6. Verificar funcionamento

1. Acesse [http://localhost:3000/login](http://localhost:3000/login) e entre com `APP_USERNAME` / `APP_PASSWORD`.
2. Abra o painel **Dashboard** (`/dashboard`).
3. No card **Fontes de Monitoramento**, a linha **Gmail — Urgência Renal** deve aparecer como **ativo**.
4. Envie um e-mail de teste para `GMAIL_USER` com o assunto exato `Notificação de novo e-mail – Urgência Renal`.
5. Em até `GMAIL_FIXED_RATE` ms (5 min por padrão), o Telegram deve receber o alerta nos chats configurados.

---

## 7. Erros comuns

| Sintoma | Causa provável | Ação |
|---|---|---|
| `AUTHENTICATIONFAILED` nos logs | 2FA desativada ou App Password incorreta. | Reabilite 2FA, gere nova App Password e atualize `GMAIL_APP_PASSWORD` (sem espaços). |
| `Connection timed out` / `UnknownHostException` | Firewall/rede bloqueia a porta 993. | Liberar saída TCP 993 para `imap.gmail.com` no firewall/host Docker. |
| Monitor ativo mas nada chega ao Telegram | Filtro de assunto não bate (acento perdido, hífen `–` vs `-`). | Confira `GMAIL_SUBJECT_FILTER`. O hífen padrão é **en-dash** (`–`, U+2013), não traço comum. |
| `Painel mostra "inativo"` | `GMAIL_ENABLED=false` ou credenciais vazias. | Definir `GMAIL_ENABLED=true`, `GMAIL_USER` e `GMAIL_APP_PASSWORD`. |
| Senha "errada" mesmo após gerar nova | Conta sem 2FA ou política de admin bloqueia App Passwords (Workspace). | Habilitar 2FA; em Workspace, peça ao admin liberar "Less secure / App passwords". |

---

## 8. Segurança

- **Nunca** commite a `GMAIL_APP_PASSWORD` no Git. O `.env` já está no `.gitignore`.
- Em produção, prefira o gerenciador de segredos da plataforma (Docker secrets, AWS SSM, etc.).
- Para revogar o acesso, vá em [https://myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords) e remova a senha — o bot perderá o acesso imediatamente.
