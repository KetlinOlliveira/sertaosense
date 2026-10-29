# Módulo 2: serviço Dispositivos

## O que foi construído e por quê

O SertãoSense monitora cisternas no sertão da Bahia e avisa a população quando o nível da água muda.
Antes de receber qualquer leitura de sensor, o sistema precisa saber **quais cisternas existem** e
**quais sensores estão instalados em cada uma**. Esse é o trabalho do serviço `dispositivos`: ele é o
cadastro do sistema.

Neste módulo construímos a base desse serviço:

1. **Um banco PostgreSQL só dele**, rodando em um container Docker. Pela decisão registrada no
   [ADR-001](../adr/ADR-001-microsservicos.md), cada microsserviço tem o próprio banco, e nenhum outro
   serviço pode acessá-lo diretamente.
2. **Uma aplicação Spring Boot** que conecta nesse banco.
3. **As tabelas `cisterna` e `sensor`**, criadas por migrations do Flyway (scripts SQL versionados),
   com regras de validação dentro do próprio banco.
4. **As entidades JPA** `Cisterna` e `Sensor`, que representam essas tabelas no Java.
5. **Uma imagem Docker da aplicação**, para que banco e aplicação subam juntos com um único comando.

Ainda **não** há endpoints REST nem repositórios. O objetivo do módulo foi deixar a fundação pronta:
banco, schema, mapeamento e container.

### Modelo de dados

- Uma **cisterna** tem nome, comunidade, capacidade em litros, latitude, longitude e um indicador
  `ativa`.
- Um **sensor** tem um código único, um tipo (`BOIA`, `ULTRASSONICO`, `PRESSAO` ou `CAPACITIVO`) e
  pertence a **uma** cisterna. Uma cisterna pode ter vários sensores.

### Regras de negócio decididas neste módulo

| Regra | Motivo |
|---|---|
| Capacidade maior que 0 e no máximo 100.000 litros | Capacidade é o *tamanho* da cisterna, não o nível de água. Zero não existe fisicamente e causaria divisão por zero no cálculo de porcentagem. O teto fica acima das cisternas de 52 mil litros (calçadão, enxurrada, escolar), mas barra erros de digitação como 16.000.000. |
| Latitude entre -90 e 90, longitude entre -180 e 180 | Limites geográficos globais. Não restringimos à Bahia para o sistema poder crescer para outros estados. |
| Nome único **dentro da mesma comunidade** | "Cisterna da Escola" pode existir em várias comunidades, mas um alerta precisa identificar uma única cisterna. |
| Cisternas não são apagadas, são **desativadas** (soft delete) | O histórico de leituras fica em outros serviços, que guardam só o UUID da cisterna. Apagar deixaria esse histórico órfão. O histórico também serve para relatórios e análises futuras com IA. |

## Conceitos usados (e onde aparecem)

### Docker Compose e infraestrutura

- **Docker Compose**: descreve vários containers em um arquivo e sobe todos com um comando.
  Arquivo: [docker-compose.yml](../../docker-compose.yml).
- **Rede interna e DNS do Compose**: os serviços de um mesmo Compose ficam numa rede privada e se
  encontram **pelo nome do serviço**. Por isso a aplicação, dentro do container, conecta em
  `postgres-dispositivos:5432`, e não em `localhost`.
  Arquivo: [docker-compose.yml](../../docker-compose.yml) (variável `SPRING_DATASOURCE_URL`).
- **Publicação de portas (`host:container`)**: `5441:5432` quer dizer "a porta 5441 da minha máquina
  leva à porta 5432 do container". Só serve para quem está **fora** do Compose. Duas aplicações não
  podem usar a mesma porta do host, e foi isso que causou os conflitos nas portas 5433 e 8081.
  Arquivo: [docker-compose.yml](../../docker-compose.yml).
- **Volume nomeado**: guarda os dados do Postgres fora do container, para que eles sobrevivam quando
  o container é recriado. Arquivo: [docker-compose.yml](../../docker-compose.yml) (`dispositivos-data`).
- **Healthcheck**: um comando que o Docker roda periodicamente para saber se o container está
  realmente pronto. Usamos `pg_isready -h 127.0.0.1`. O `-h` força o teste por TCP, porque na primeira
  inicialização o Postgres sobe um servidor temporário só no socket local.
  Arquivo: [docker-compose.yml](../../docker-compose.yml).
- **`depends_on` com `condition: service_healthy`**: faz a aplicação esperar o banco ficar saudável,
  e não apenas o container iniciar. "Container rodando" é diferente de "aplicação pronta".
  Arquivo: [docker-compose.yml](../../docker-compose.yml).
- **Segredos no `.env`**: as credenciais ficam num arquivo `.env` que não vai para o git
  ([.gitignore](../../.gitignore)) nem para a imagem Docker ([.dockerignore](../../.dockerignore)).
  O Compose lê o `.env` sozinho, e o `${VAR:?mensagem}` faz o Compose falhar se faltar uma variável.

### Maven multi-módulo

- **POM pai e herança**: o [pom.xml](../../pom.xml) da raiz herda do `spring-boot-starter-parent`,
  que define versões compatíveis de todas as bibliotecas. O
  [dispositivos/pom.xml](../../dispositivos/pom.xml) herda do pai, por isso as dependências não têm
  `<version>`.
- **`-pl` e `-am`**: `-pl dispositivos` escolhe o módulo. `-am` inclui também os módulos dos quais ele
  depende. Para rodar a aplicação, usamos só `-pl`: com `-am`, o goal `spring-boot:run` também é
  executado no pom pai, que não tem classe `main`, e o build falha.
- **Flyway 10+ separado por banco**: além do `flyway-core`, é preciso `flyway-database-postgresql`.
  Arquivo: [dispositivos/pom.xml](../../dispositivos/pom.xml).
- **Lombok como processador de anotações**: gera getters e construtores **durante a compilação** e
  não vai para o jar final. Arquivo: [dispositivos/pom.xml](../../dispositivos/pom.xml).

### Configuração do Spring Boot

- **Importar o `.env` como properties**: `spring.config.import: optional:file:../.env[.properties]`
  faz o Spring ler o `.env` sem nenhuma biblioteca extra.
  Arquivo: [application.yml](../../dispositivos/src/main/resources/application.yml).
- **Precedência de configuração**: variáveis de ambiente vencem o `application.yml`.
  `SPRING_DATASOURCE_URL` sobrescreve `spring.datasource.url` (isso se chama *relaxed binding*). É assim
  que o mesmo código conecta em `localhost:5441` na sua máquina e em `postgres-dispositivos:5432` no
  container.
- **`ddl-auto: validate`**: o Hibernate **não cria** tabelas, só confere se as entidades batem com o
  schema que o Flyway criou. Se não baterem, a aplicação não sobe.
  Arquivo: [application.yml](../../dispositivos/src/main/resources/application.yml).
- **`open-in-view: false`**: desliga o padrão que mantém a sessão do banco aberta durante toda a
  requisição HTTP, o que esconde consultas lazy e prende conexões do pool.

### Flyway (migrations)

- **Migration versionada**: arquivos `V<n>__<descricao>.sql` em `db/migration`, aplicados em ordem e
  registrados na tabela `flyway_schema_history`.
  Pasta: [db/migration](../../dispositivos/src/main/resources/db/migration).
- **Imutabilidade e checksum**: o Flyway guarda um checksum de cada migration aplicada. Editar um
  arquivo já aplicado causa `checksum mismatch` e a aplicação não sobe. Mudanças viram uma **nova**
  migration, como fizemos com a
  [V3](../../dispositivos/src/main/resources/db/migration/V3__regras_de_validacao.sql).
- **Constraints no banco como última linha de defesa**: `CHECK`, `UNIQUE` e `FOREIGN KEY` protegem os
  dados mesmo contra bugs ou `INSERT` manual. A API também vai validar, mas para dar mensagens
  amigáveis.
  Arquivos: [V2](../../dispositivos/src/main/resources/db/migration/V2__criar_tabela_sensor.sql) e
  [V3](../../dispositivos/src/main/resources/db/migration/V3__regras_de_validacao.sql).
- **Índice em chave estrangeira**: o PostgreSQL **não** cria índice automático para FK (mas cria para
  `UNIQUE` e `PRIMARY KEY`). Criamos `idx_sensor_cisterna_id` manualmente.
  Arquivo: [V2](../../dispositivos/src/main/resources/db/migration/V2__criar_tabela_sensor.sql).
- **`ADD COLUMN ... NOT NULL DEFAULT`**: o `DEFAULT` preenche linhas que já existem. Sem ele, um
  `NOT NULL` numa tabela com dados falharia.
  Arquivo: [V3](../../dispositivos/src/main/resources/db/migration/V3__regras_de_validacao.sql).

### JPA e Lombok

- **UUID gerado pela aplicação**: `@GeneratedValue(strategy = GenerationType.UUID)`. O ID existe antes
  do `INSERT` e não revela quantos registros existem.
  Arquivos: [Cisterna.java](../../dispositivos/src/main/java/br/com/sertaosense/dispositivos/dominio/Cisterna.java)
  e [Sensor.java](../../dispositivos/src/main/java/br/com/sertaosense/dispositivos/dominio/Sensor.java).
- **`@Getter` em vez de `@Data`**: o `@Data` gera `equals`, `hashCode` e `toString` com todos os campos,
  o que dispara carregamentos lazy, pode entrar em loop em relacionamentos e quebra coleções `Set`.
- **Construtor vazio `protected`**: o JPA precisa dele, mas o resto do código fica obrigado a usar o
  construtor com os campos obrigatórios.
- **Sem setters públicos**: mudanças de estado devem entrar por métodos com nome de negócio (como um
  futuro `desativar()`).
- **`BigDecimal` para `NUMERIC`**: latitude e longitude são `NUMERIC(9,6)`. Com `Double`, o `validate`
  acusa tipo incompatível.
- **Padrão no Java além do banco**: o `DEFAULT TRUE` da coluna `ativa` só vale quando o `INSERT` omite
  a coluna. O Hibernate sempre envia todos os campos, então o Java também inicializa `ativa = true`.
- **`@Enumerated(EnumType.STRING)`**: grava `"BOIA"` em vez da posição no enum (0, 1, 2...), que
  mudaria de significado se alguém reordenasse os valores.
  Arquivos: [Sensor.java](../../dispositivos/src/main/java/br/com/sertaosense/dispositivos/dominio/Sensor.java)
  e [TipoSensor.java](../../dispositivos/src/main/java/br/com/sertaosense/dispositivos/dominio/TipoSensor.java).
- **`@ManyToOne(fetch = LAZY)`**: o padrão do `@ManyToOne` é EAGER, que faz JOIN em toda consulta e é
  uma fonte clássica do problema N+1.
- **Relacionamento unidirecional**: o Sensor conhece a Cisterna, mas a Cisterna não tem uma lista de
  sensores. A busca "sensores da cisterna X" vai ser uma consulta explícita no repositório.

### Imagem Docker

- **Build multi-stage**: o primeiro estágio compila com Maven e JDK. O segundo só recebe o jar e roda
  com um JRE. A imagem final fica menor e sem ferramentas desnecessárias.
  Arquivo: [Dockerfile](../../dispositivos/Dockerfile).
- **Cache de camadas**: copiar os `pom.xml` antes do código faz o Docker reaproveitar as dependências
  já baixadas quando só o código muda.
- **Contexto de build na raiz**: o Dockerfile fica em `dispositivos/`, mas o contexto é a raiz,
  porque o build precisa do pom pai.
- **Usuário não root**: se alguém explorar uma falha na aplicação, não ganha root no container.

## Fluxo: o que acontece ao rodar `docker compose up --build`

Ainda não existe requisição HTTP de negócio, então o fluxo mais importante deste módulo é a
**inicialização**:

1. O Compose lê o [docker-compose.yml](../../docker-compose.yml) e substitui as variáveis
   `${DISPOSITIVOS_DB_...}` pelos valores do `.env`. Se faltar alguma, ele para com erro.
2. O Docker builda a imagem do serviço usando o [Dockerfile](../../dispositivos/Dockerfile): baixa as
   dependências, compila, gera o jar e copia só o jar para a imagem final.
3. O Compose cria a rede interna e sobe o container `postgres-dispositivos`.
4. Na primeira vez, o Postgres cria o banco e o usuário a partir de `POSTGRES_DB`, `POSTGRES_USER` e
   `POSTGRES_PASSWORD` e guarda tudo no volume `dispositivos-data`.
5. O healthcheck roda `pg_isready` a cada 5 segundos. Quando ele passa, o container fica `healthy`.
6. Só então, por causa do `depends_on: service_healthy`, o Compose sobe o container `dispositivos`.
7. O Spring Boot inicia. A variável `SPRING_DATASOURCE_URL` sobrescreve a URL do
   [application.yml](../../dispositivos/src/main/resources/application.yml) e aponta para
   `postgres-dispositivos:5432`.
8. O HikariCP abre o pool de conexões com o banco.
9. O Flyway consulta `flyway_schema_history`, compara com os arquivos em `db/migration` e aplica as
   migrations que faltam (V1, V2, V3), na ordem. Se alguma já aplicada tiver mudado, ele para com
   `checksum mismatch`.
10. O Hibernate, com `ddl-auto: validate`, compara as entidades
    [Cisterna](../../dispositivos/src/main/java/br/com/sertaosense/dispositivos/dominio/Cisterna.java) e
    [Sensor](../../dispositivos/src/main/java/br/com/sertaosense/dispositivos/dominio/Sensor.java) com
    as tabelas. Se um tipo ou coluna não bater, a aplicação não sobe.
11. O Tomcat embutido sobe na porta 8091, e o log mostra `Started DispositivosApplication`.

E o que acontece quando um dado inválido tenta entrar (por exemplo, uma cisterna com capacidade 0):
o `INSERT` chega ao PostgreSQL, que verifica as constraints da tabela, encontra
`ck_cisterna_capacidade` violada e recusa a linha inteira. Nada é gravado.

## Problemas que apareceram (e o que ensinaram)

| Problema | Causa | Lição |
|---|---|---|
| `Bind for 0.0.0.0:5433 failed: port is already allocated` | Outro projeto já usava a porta 5433 no host. | Uma porta do host só pode ter um dono. O container chegou a ficar `healthy` **sem** a porta publicada: healthy não significa acessível de fora. |
| `Unable to find a suitable main class` | `mvn -pl dispositivos -am spring-boot:run` rodou o goal também no pom pai. | `-am` serve para build, não para goals que executam a aplicação. |
| Porta 8081 em uso | Outra aplicação Spring Boot da máquina usava a 8081. | Uma faixa de portas própria para o projeto (544x para bancos, 809x para serviços) evita colisões. |
| Confusão entre capacidade e nível | Capacidade é o tamanho da cisterna; nível é a água atual. | Uma cisterna seca tem nível 0, mas continua com capacidade de 16.000 litros. Nível 0 é tratado pela análise como alerta. |

## Perguntas que um entrevistador faria

1. Por que cada microsserviço deve ter o próprio banco de dados, e quais custos você aceita com essa
   escolha?
2. Qual a diferença entre `depends_on` simples e `depends_on` com `condition: service_healthy`? Isso
   resolve o problema de o banco cair depois que a aplicação já subiu?
3. Por que usar Flyway com `ddl-auto: validate` em vez de deixar o Hibernate criar as tabelas com
   `ddl-auto: update`? O que acontece se você editar uma migration já aplicada?
4. Por que não usar `@Data` do Lombok em entidades JPA, e por que trocar o `fetch` padrão do
   `@ManyToOne`?
5. Como a mesma aplicação conecta no banco em `localhost` quando roda na sua máquina e em outro
   endereço quando roda no Docker Compose, sem mudar o código?
