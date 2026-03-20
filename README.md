# RuiWen 

RuiWen 是一个基于 Spring Boot 和 Spring AI 构建的现代化知识社区平台。

## 🛠 技术栈

*   **开发语言**：Java 21
*   **核心框架**：Spring Boot 3.2.4
*   **AI 框架**：Spring AI
*   **数据库**：MySQL 8.0+
*   **ORM 框架**：MyBatis
*   **搜索与向量库**：Elasticsearch
*   **消息队列**：Apache Kafka
*   **缓存**：Redis, Caffeine
*   **数据同步**：Alibaba Canal
*   **对象存储**：Aliyun OSS
*   **工具库**：Lombok, Maven

## 🚀 快速开始

### 前置条件

请确保您的环境中已运行以下服务（推荐使用 Docker）：

*   MySQL (端口 3306)
*   Redis (端口 6379)
*   Kafka (端口 9092)
*   Elasticsearch (端口 9201)
*   Canal (端口 11111)

### 配置说明

请根据您的本地环境更新 `src/main/resources/application.yml` 配置文件。

**关键配置项：**

*   **数据库**：`spring.datasource` (url, username, password)
*   **Redis**：`spring.data.redis`
*   **Kafka**：`spring.kafka`
*   **Elasticsearch**：`spring.elasticsearch.uris`
*   **AI 密钥**：
    *   `spring.ai.deepseek.api-key`
    *   `spring.ai.openai.api-key`
*   **OSS**：`oss` (endpoint, keys, bucket)
*   **Canal**：`canal` (host, port, destination)

### 运行应用

1.  **生成密钥**：确保 `src/main/resources/keys/` 目录下存在用于 JWT 签名的 RSA 密钥对 (`private.pem`, `public.pem`)。
2.  **构建项目**：
    ```bash
    mvn clean package
    ```
3.  **启动应用**：
    ```bash
    java -jar target/RuiWen-1.0-SNAPSHOT.jar
    ```
    或者直接在 IDE 中运行 `RuiWenApplication.java`。

## 📂 项目结构

*   `auth`：认证逻辑（JWT、登录）。
*   `llm`：大语言模型集成相关代码。
*   `knowpost`：知识帖子业务逻辑。
*   `search`：搜索服务集成。
*   `counter`：分布式计数服务。
*   `cache`：缓存工具与配置。
*   `storage`：文件存储服务。
*   `relation`：用户关系管理。

---
*Powered by Spring Boot & Spring AI*
