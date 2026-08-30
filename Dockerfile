# ============================================================================
# 多阶段构建：Maven 打包 → JRE 运行
# 构建：docker build -t lease-platform .
# 运行：docker-compose up -d（推荐，见 docker-compose.yml）
# ============================================================================

# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# 先拷贝 pom 与 wrapper，利用层缓存
COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw ./
RUN ./mvnw -q -DskipTests dependency:go-offline || true
# 拷贝源码并打包
COPY src ./src
RUN ./mvnw -q -DskipTests package

# ---- 运行阶段 ----
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m" \
    SPRING_PROFILES_ACTIVE=prod
COPY --from=build /build/target/lease-platform-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
