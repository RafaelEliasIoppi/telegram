# Copilot Instructions - Telegram Bot Spring Boot

## Visão Geral da Arquitetura

Este é um bot Telegram construído com Spring Boot 3+ que envia mensagens automáticas e sob demanda. A aplicação possui três mecanismos de envio:

1. **Interface Web (Thymeleaf)**: Formulário HTML em `/` para envio manual via browser
2. **REST API**: Endpoint `POST /enviar` com parâmetros `titulo`, `conteudo`, `destinatario`
3. **Tarefas Agendadas**: `@Scheduled` tasks em `ScheduledTasks.java` para mensagens periódicas

**Fluxo principal**: Controller → TelegramService → Telegram Bot API (REST)

## Estrutura de Código

- **Pacote base**: `telegram.teste`
- **Entry point**: [TesteApplication.java](teste/src/main/java/telegram/teste/TesteApplication.java) com `@EnableScheduling`
- **Services**: [TelegramService.java](teste/src/main/java/telegram/teste/service/TelegramService.java) (core), [DefesaCivilMonitor.java](teste/src/main/java/telegram/teste/service/DefesaCivilMonitor.java) (web scraping)
- **Controller**: [MessageController.java](teste/src/main/java/telegram/teste/controller/MessageController.java) - roteamento web + API

## Configuração Essencial

**application.properties obrigatórios**:
```properties
telegram.bot.token=<BOT_TOKEN>  # Obtido via @BotFather
telegram.chat.id=<CHAT_ID>      # Obtido via /getUpdates
server.port=3000
```

Para obter `chat.id`: envie uma mensagem ao bot e acesse `https://api.telegram.org/bot<TOKEN>/getUpdates`

## Build & Run

```bash
# Working directory: /workspaces/telegram/teste
mvn clean install
mvn spring-boot:run

# Acesso: http://localhost:3000/
```

**⚠️ Problema Conhecido**: DefesaCivilMonitor requer dependência JSoup que está FALTANDO no pom.xml. Para compilar:

```xml
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>
```

Adicione ao `<dependencies>` em [pom.xml](teste/pom.xml#L36) antes de rodar.

## Padrões de Código

### Injeção de Dependências
Sempre use `@Autowired` para serviços - evite `new` para beans Spring:
```java
@Autowired
private TelegramService telegramService;
```

### Tarefas Agendadas
Use cron expressions ou `fixedRate` (ms) em [ScheduledTasks.java](teste/src/main/java/telegram/teste/ScheduledTasks.java):
```java
@Scheduled(cron = "0 0 9 * * *")        // Diário 9h
@Scheduled(fixedRate = 1800000)         // A cada 30min
```

### Formatação de Mensagens
Prefixe mensagens com emojis para contexto visual:
```java
"📢 " + titulo  // Anúncios gerais
"☀️ Bom dia"   // Saudações
"⚠️ Alerta"    // Avisos críticos
```

### Tratamento de Erros
TelegramService usa try-catch com logging no console - não há retry automático. Falhas são logadas mas não interrompem a aplicação.

## Integrações Externas

1. **Telegram Bot API**: `https://api.telegram.org/bot{token}/sendMessage` - POST JSON com `chat_id` e `text`
2. **Defesa Civil RS**: Web scraping periódico via JSoup (fixedRate=10min) no [DefesaCivilMonitor.java](teste/src/main/java/telegram/teste/service/DefesaCivilMonitor.java)

**Atenção**: DefesaCivilMonitor.verificarAgora() referenciado em MessageController não existe - use verificarAlertas() ou implemente o método.

## HTML/CSS
Templates Thymeleaf em `src/main/resources/templates/`:
- [index.html](teste/src/main/resources/templates/index.html): Formulário com gradiente CSS
- [sucesso.html](teste/src/main/resources/templates/sucesso.html): Página de confirmação

## Convenções
- Mensagens em português (pt-BR)
- Porta padrão: 3000
- Sem autenticação/autorização implementada
- RestTemplate (não WebClient) para HTTP
