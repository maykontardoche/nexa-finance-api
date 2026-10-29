# syntax=docker/dockerfile:1
# Imagem da Nexa Finance API. Build e runtime fixados por tag + digest (build
# reproduzível; atualizar o digest junto com a tag). Nenhum segredo entra na imagem: a
# configuração vem do ambiente do contêiner (compose.yaml).

FROM eclipse-temurin:25-jdk@sha256:3ab7fc8d0b4ade8fef96903fb36767c1dbb4fd1535dcfad85b59c17afbbb5857 AS build
WORKDIR /workspace

# Sem unzip no PATH, o mvnw troca a distribuição .zip pela .tar.gz, cujo hash não é o do
# distributionSha256Sum, e recusa o download. Só no estágio de build: a imagem final não leva.
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

# Dependências primeiro: a camada só é refeita quando o pom ou o wrapper mudam.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q dependency:go-offline

COPY src/ src/
# Testes fora do build da imagem: os de integração precisam de Docker (Testcontainers), que não
# existe aqui dentro. Eles rodam no `./mvnw verify`, antes de a imagem ser construída.
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/nexa-finance-api-*.jar extract --layers --application-filename app.jar --destination extracted

FROM eclipse-temurin:25-jre@sha256:78f1f183913482ef390c2722f46c3f0ce76ada341d69590e7c482ec943ee72d5

# Usuário próprio, sem shell nem home: o processo nunca roda como root.
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
# Camadas do jar, da que menos muda para a que mais muda. Os arquivos ficam com o root como
# dono: o usuário app só lê.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app:app
ENV SPRING_PROFILES_ACTIVE=container
EXPOSE 8080

# A imagem não tem curl nem wget; o bash fala HTTP direto pelo /dev/tcp. O health leva ~2 s
# para responder DOWN (connection-timeout do Hikari), por isso o timeout de 5 s. A porta é a
# interna, sempre 8080 (SERVER_PORT no Compose só muda a porta publicada no host).
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --start-interval=2s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n' >&3 && head -n 1 <&3 | grep -q ' 200 '"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
