# Maven Docker Wrapper

## Goal

当前环境没有本机 `java` 和 `mvn`，但有 Docker。为避免先装本地 JDK/Maven，仓库新增了一个轻量级 wrapper：

- `ecm-core/mvnw`

它不是标准 Maven Wrapper 下载器，而是直接使用 Docker Maven 镜像执行命令。

## Usage

在 `ecm-core/` 下执行：

```bash
./mvnw -version
./mvnw test
./mvnw test -Dtest=NodeServiceMoveConsistencyTest,SearchIndexServiceSubtreeReindexTest,SecurityServicePermissionMutationTest,EcmEventListenerPermissionIndexingTest
./mvnw clean package -DskipTests
```

也可以在仓库根目录执行：

```bash
./ecm-core/mvnw -version
./ecm-core/mvnw test
```

## Defaults

- Maven image: `maven:3.9-eclipse-temurin-17`
- Host Maven cache: `ecm-core/.m2-cache`

可通过环境变量覆盖：

```bash
MAVEN_DOCKER_IMAGE=maven:3.9.9-eclipse-temurin-21 ./ecm-core/mvnw -version
MAVEN_DOCKER_M2_DIR=/tmp/athena-m2 ./ecm-core/mvnw test
```

## Notes

1. 第一次运行会拉取 Maven 镜像，耗时取决于网络和本机 Docker 缓存。
2. 该 wrapper 依赖 Docker daemon 可用。
3. 它解决的是“本机没有 Maven/JDK”的问题，不替代 CI。
