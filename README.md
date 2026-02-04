# telegram# Projeto: Bot Telegram com Spring Boot

Este projeto é uma aplicação **Spring Boot** que envia mensagens para o Telegram usando a **Telegram Bot API**.  
Ele possui:
- Um formulário HTML (`index.html`) para enviar mensagens manualmente.
- Um endpoint REST (`/enviar`) para integração via API.
- Tarefas agendadas (`ScheduledTasks`) que disparam mensagens automáticas.

---

## 🚀 Tecnologias utilizadas
- Java 17+
- Spring Boot 3+
- Thymeleaf (para páginas HTML)
- RestTemplate (para chamadas HTTP)
- Telegram Bot API

---

## ⚙️ Configuração

No arquivo `src/main/resources/application.properties`, configure:

```properties
spring.application.name=teste

# Token do seu bot (copiado do BotFather)
telegram.bot.token=

# Chat ID do usuário ou grupo que receberá as mensagens
telegram.chat.id=7648003850

# Porta da aplicação
server.port=3000
⚠️ O chat_id é obtido acessando:

Código
https://api.telegram.org/bot<SEU_TOKEN>/getUpdates
após enviar uma mensagem para o bot no Telegram.

▶️ Como rodar
Clone o repositório:

bash
git clone https://github.com/seuusuario/seuprojeto.git
cd seuprojeto
Compile e rode:

bash
mvn clean install
mvn spring-boot:run
Acesse no navegador:

Código
http://localhost:3000/
📄 Estrutura do projeto
Código
src/main/java/telegram/teste/
 ├── TesteApplication.java       # Classe principal
 ├── service/TelegramService.java # Serviço que envia mensagens
 ├── controller/MessageController.java # Controller para formulário/API
 └── ScheduledTasks.java          # Mensagens automáticas

src/main/resources/templates/
 ├── index.html   # Formulário para enviar mensagem
 └── sucesso.html # Página de confirmação
🎯 Funcionalidades
Enviar mensagens via formulário HTML.

Enviar mensagens via requisição POST (/enviar).

Disparar mensagens automáticas em horários definidos.