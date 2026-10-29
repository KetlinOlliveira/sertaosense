# Build multi-stage: o estágio 1 compila com Maven + JDK; o estágio 2 só roda o jar com um JRE.
# A imagem final não carrega Maven, código-fonte nem o JDK completo (menor e com menos superfície de ataque).
# Contexto de build = raiz do repositório (precisa do pom pai). Ver "build" no docker-compose.yml.

# ---------- Estágio 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copia só os poms primeiro e baixa as dependências. Enquanto os poms não mudarem, o Docker
# reaproveita esta camada do cache e o build não baixa tudo de novo a cada mudança no código.
COPY pom.xml .
COPY dispositivos/pom.xml dispositivos/
RUN mvn -B -f dispositivos/pom.xml dependency:go-offline

# Agora o código. -f builda só este módulo (o pai é lido de ../pom.xml), sem exigir que as pastas
# dos outros serviços existam. -DskipTests: os testes rodam no pipeline, não no build da imagem.
COPY dispositivos/src dispositivos/src
RUN mvn -B -f dispositivos/pom.xml package -DskipTests

# ---------- Estágio 2: runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Não rodar como root dentro do container
RUN useradd --system --no-create-home spring
USER spring

COPY --from=build /app/dispositivos/target/dispositivos-*.jar app.jar

EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]
