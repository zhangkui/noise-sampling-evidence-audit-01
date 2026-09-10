# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先拷贝 pom 利用 Docker 层缓存预下载依赖（go-offline 对可选插件元数据可能返回非零，忽略即可）
COPY pom.xml .
RUN mvn -q -B dependency:go-offline || true

COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# 容器内健康检查（不依赖本机 curl，镜像自带 bash）
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/target/app.jar /app/app.jar

EXPOSE 8080

# 应用就绪探测：参数校验失败也会返回统一 JSON(code 字段)，证明 Web 层已可用
HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=12 \
  CMD curl -fs -X POST http://localhost:8080/api/auth/login \
    -H 'Content-Type: application/json' -d '{}' | grep -q '"code"'

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
