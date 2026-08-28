# 租赁小程序平台（lease-platform）—— Spring Boot 4 / Java 21 / Maven
# 常用：make run（启动项目） make build（打包） make db-reset（重置数据库）
# 全部命令：make help

.PHONY: help run stop compile test build run-jar db-reset

# Maven 调用：把 Maven 本地仓库重定向到工作区 .m2home（已 gitignore），
# 受限环境（沙箱等无法写 ~/.m2）与正常开发环境同样适用；
# 如需追加参数：make MVN_ARGS="-X" run
MVN := MAVEN_USER_HOME=$(CURDIR)/.m2home ./mvnw -Dmaven.repo.local=$(CURDIR)/.m2home/repository
JAR := target/lease-platform-0.0.1-SNAPSHOT.jar

help: ## 显示所有命令
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-12s %s\n", $$1, $$2}'

run: ## 启动项目（开发模式，前台运行，端口 8080）
	$(MVN) spring-boot:run $(MVN_ARGS)

stop: ## 停止 :8080 端口上的服务
	-@lsof -ti :8080 | xargs kill -9 2>/dev/null

compile: ## 编译（跳过测试）
	$(MVN) -DskipTests compile $(MVN_ARGS)

test: ## 运行测试
	$(MVN) test $(MVN_ARGS)

build: ## 打包可执行 jar（跳过测试）
	$(MVN) -DskipTests package $(MVN_ARGS)

run-jar: ## 运行已打包的 jar（需先 make build）
	java -jar $(JAR)

db-reset: ## 重置数据库：建库（不存在时）→ 清空表 → 按依赖顺序建表
	./reset_db.sh
