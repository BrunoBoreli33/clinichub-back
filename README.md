<h1 align="center" style="font-weight: bold;">WhatsApp CRM 💻</h1>

<p align="center">
 <a href="#tech">Tecnologias</a> • 
 <a href="#started">Começando</a> • 
  <a href="#routes">API Endpoints</a> •
 <a href="#contribute">Contribuir</a>
</p>

<p align="center">
    <b>Plataforma de CRM integrada à API do WhatsApp que automatiza toda a gestão e nutrição de leads, otimizando o atendimento e aumentando a conversão de clientes para empresas.</b>
</p>

<h2 id="technologies">💻 Tecnologias</h2>

### <h3 align="left">Framework e Linguagem:</h3>

- Java 17
- Spring Boot 3.2.4
- Spring Framework (Web, Security, Data JPA, Validation, Retry, Aspects)

### <h3 align="left">Banco de Dados:</h3>

- PostgreSQL 42.6.0
- Flyway (Migrações de banco de dados)
- Spring Data JPA / Hibernate (ORM)

### <h3 align="left">Segurança e Autenticação:</h3>

- Spring Security
- Auth0 Java JWT 4.4.0 (Geração e validação de tokens JWT)

### <h3 align="left">Utilitários e Bibliotecas:</h3>

- Lombok (Redução de boilerplate)
- Jackson (Serialização/deserialização JSON)
- Jackson Datatype JSR310 (Suporte para LocalDateTime)
- Spring Dotenv 4.0.0 (Variáveis de ambiente)
- ZXing 3.5.1 (Geração de QR Codes)
- Caffeine (Cache em memória)
- Spring Retry (Retry automático)
- Spring Aspects (AOP - Programação Orientada a Aspectos)

### <h3 align="left">Comunicação e Email:</h3>

- Resend Java 3.1.0 (Serviço de envio de emails)
- RestTemplate/WebClient (Cliente HTTP)

### <h3 align="left">Documentação e Monitoramento:</h3>

- SpringDoc OpenAPI 2.3.0 (Documentação Swagger/OpenAPI)
- Spring Boot Actuator (Métricas e health checks)

### <h3 align="left">Desenvolvimento:</h3>

- Spring Boot DevTools (Hot reload)
- Maven (Gerenciamento de dependências)

### <h3 align="left">Agendamento:</h3>

- Spring Scheduling (@EnableScheduling)
- Spring Async (@EnableAsync)

### <h3 align="left">FUNCIONALIDADES:</h3>

- Autenticação e Autorização: Login, registro, JWT tokens, refresh tokens
- Gerenciamento de Usuários: Perfis, preferências, emails
- Recuperação de Senha: Códigos de reset, validação
- Campanhas: Criação e gerenciamento de campanhas
- Mensagens/Chat: Sistema de mensagens com WhatsApp
- Tarefas: Sistema de tarefas agendadas
- Tags: Sistema de etiquetas/tags
- Rotinas Automatizadas: Textos automáticos programados
- Webhooks: Integração com serviços externos
- Notificações: Sistema de notificações
- Instâncias Web: Gerenciamento de instâncias (provavelmente WhatsApp)
- Dashboard: Estatísticas e métricas
- Textos Pré-configurados: Templates de mensagens
- Integração ZAPI: API externa para WhatsApp

---

<h2 id="started">🚀 Começando</h2>

<h3>Pré-Requisitos</h3>


- Java 17 SDK
- Maven
- PostgreSQL versão 15.16
- Z-API
- Resend
- Configure um domínio no Resend para o envio de e-mails (Você deverá ter um domínio)

---

<h3>Para rodar o Backend Utilizaremos o Intellij Pro, e devemos configurar as seguintes variáveis de ambiente:</h3>

- DB_PASSWORD -> Senha do seu banco de dados PostgreSQL
- RESEND_API_KEY -> Chave API do Resend

---

<h3>Após abrir o projeto do backend utilizando o intellij:</h3>

- Abrir o application.properties e configurar o parâmetro "spring.datasource.url" (para o mesmo do seu banco de dados)
- Instalar FFMPEG -> Para isso deve-se ter instalado o gerenciador de pacotes chocolatey, depois deve-se abrir o PowerShell no modo admin e digitar o comando "choco install ffmpeg -y"
- Para rodar o FFMPEG no Intellij, deve-se adicionar variável de ambiente: "C:\ProgramData\chocolatey\bin", no sistema operacional.
- Ir na pasta "Services" e em "EmailService" e configurar todos os emails que começam com "contato@". E colocar o nome do seu domínio depois do "@", ficando por exemplo "contato@patriciafernanda.com". (Esse é o mesmo domínio que foi registrado na sua conta do resend).
- A Documentação da API está feita em swagger na url: http://localhost:8081/swagger-ui/index.html#/
- Configurar o z-api (https://app.z-api.io/app)
- Depois de fazer o cadastro, vá para a página "https://app.z-api.io/app/security" e crie o token de segurança da conta.

---

<h3>Agora para conectar a instância do Z-API á nossa API do WhatsApp CRM:</h3>

- Após rodar o Backend e o Frontend, ir na URL "http://localhost:8080/admin" e inserir as informações do Z-API nos campos de "Adicionar Instância"
- Logo após, ir na url "http://localhost:8080/dashboard" e scanear o QRCode do mesmo jeito que conecta ao whatsapp web.

---

<h3>Por fim, para receber mensagens no sistema, precisamos configurar o Ngrok (se quiser receber as mensagem em ambiente localhost):</h3>

<h3>Na instância do Z-API, na aba "WebHook e Configurações Gerais", se for usar ngrok, colocar uma url abaixo no campo "Ao Receber" do z-api, e é necessário ativar a opção "Notificar as Enviadas por mim também"</h3>

- Ngrok: https://deloras-achromatous-mathilde.ngrok-free.dev/webhook/message

<h3>E caso for configurar em nuvem, a url ficará algo como:</h3>

- Vercel: https://api.hubcrm.com/webhook/message

---

<h3>Clonagem</h3>

Como clonar seu projeto

```bash
git clone git@github.com:BrunoBoreli33/clinichub-back.git
```

<h3>Iniciando o Projeto</h3>

Para Startar o Frontend, digite no terminal:

```bash
npm run dev
```

<h2 id="routes">📍 API Endpoints</h2>

Todos os EndPoints estão disponíveis após rodar o backend e o frontend na url:

```bash
http://localhost:8081/swagger-ui/index.html#/
```


<h2 id="contribute">📫 Contribuir</h2>

1. `git clone git@github.com:BrunoBoreli33/clinichub-back.git`
2. Crie uma branch para cada funcionalidade ou correção nova
3. Siga os padrões de commit
4. Abra um Pull Request explicando o problema resolvido ou a funcionalidade implementada, se houver, anexe uma captura de tela das modificações visuais e aguarde a revisão!


