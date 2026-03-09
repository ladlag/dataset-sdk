# Knowledge SDK

Knowledge SDK 是一个 SpringBoot 知识库 SDK，封装了对知识库系统的 HTTP 调用，让业务代码只需要调用 `KnowledgeDatasetService` 即可完成知识库操作，无需关心 token 管理、SSO 登录、HTTP 调用、知识库创建等细节。

## 技术要求

- JDK 1.8
- SpringBoot 2.7.x
- HTTP 客户端：OkHttp
- 文件类型：MultipartFile
- 线程安全（token 刷新、dataset 缓存）
- 支持至少 20 并发

## 集成方式

### 1. 添加 Maven 依赖

先将 SDK 安装到本地仓库：

```bash
cd knowledge-sdk
mvn clean install
```

然后在你的 SpringBoot 项目 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>com.knowledge</groupId>
    <artifactId>knowledge-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 添加配置

在 `application.yml` 或 `application.properties` 中配置。SDK 源码中提供了完整的示例配置文件 [`application-example.yml`](src/main/resources/application-example.yml)，你可以将其中的 `knowledge:` 部分复制到你项目的 `application.yml` 中，然后根据实际情况修改。

```yaml
knowledge:
  base-url: https://your-knowledge-system-host:port
  system-token: your-system-token    # 系统级 Token，用于 SSO 登录请求的 Authorization 头
  username: admin                    # SSO 登录默认用户名
  email: admin@example.com           # SSO 登录默认邮箱
  # 可选配置（以下为默认值）
  token-expiry-buffer-seconds: 300
  connect-timeout-seconds: 30
  read-timeout-seconds: 60
  write-timeout-seconds: 60
  max-file-size: 20971520          # 20MB
  max-files-per-batch: 10
  public-dataset-name: public_dataset
  user-dataset-prefix: user_
  rsa-public-key-path: rsa_public_key.pem  # RSA 公钥文件路径（classpath 下）
  # API 路径配置（以下为默认值）
  sso-login-path: /tenant/api/app/account/sso_login  # SSO 登录接口路径
  token-ttl-seconds: 3600                             # Token 有效期（秒）
  dataset-init-path: /console/api/datasets/init       # 创建/初始化知识库接口路径
  file-upload-path: /console/api/files/upload         # 文件上传接口路径
  dataset-list-path: /console/api/datasets            # 知识库列表接口路径
  dataset-by-id-path: /console/api/datasets/{datasetId}  # 单个知识库操作路径
  dataset-documents-path: /console/api/datasets/{datasetId}/documents  # 知识库文档操作路径
  dataset-document-by-id-path: /console/api/datasets/{datasetId}/documents/{documentId}  # 单个文档操作路径
  # OkHttp 连接池配置（以下为默认值）
  max-requests: 64                                    # 最大并发请求数
  max-requests-per-host: 20                           # 每个主机最大并发请求数
  connection-pool-size: 20                            # 连接池大小
  connection-pool-keep-alive-minutes: 5               # 连接保活时间（分钟）
  # 文档处理配置（以下为默认值）
  indexing-technique: high_quality                    # 索引技术
  doc-form: text_model                                # 文档形式
  doc-language: English                               # 文档语言
  process-rule-mode: custom                           # 处理规则模式
  segment-separator: "\\n\\n"                         # 分段分隔符
  segment-max-tokens: 500                             # 每段最大 token 数
  segment-chunk-overlap: 50                           # 分段重叠 token 数
  search-method: hybrid_search                        # 检索方式
  top-k: 3                                            # 检索返回 Top-K
  score-threshold-enabled: false                      # 是否启用分数阈值
  score-threshold: 0.5                                # 分数阈值
  reranking-enable: true                              # 是否启用重排序
  reranking-mode: reranking_model                     # 重排序模式
  reranking-provider-name: langgenius/tongyi/tongyi   # 重排序模型提供商
  reranking-model-name: gte-rerank-v2                 # 重排序模型名称
  weight-type: customized                             # 权重类型
  vector-weight: 0.7                                  # 向量权重
  keyword-weight: 0.3                                 # 关键词权重
  embedding-model: text-embedding-v2                  # 嵌入模型
  embedding-model-provider: langgenius/tongyi/tongyi   # 嵌入模型提供商
```

> **说明**：
> - `system-token`：系统级授权令牌，SDK 在调用 SSO 登录接口时作为 `Authorization: Bearer {systemToken}` 发送。该 token 由知识库系统管理员提供，用于验证 SDK 的调用权限。它**不是**用户的 access_token，而是一个固定的系统凭证。
> - `username` 和 `email`：默认 SSO 登录用户信息。SDK 会将其构建为 `{"username":"...","email":"..."}` JSON 格式，经 RSA 加密后作为 `HTTP_USER_INFO` 发送到 SSO 接口，换取该用户的 `access_token`。

### 3. 注入 SDK 服务

```java
import com.knowledge.sdk.service.KnowledgeDatasetService;

@Service
public class YourBusinessService {

    @Autowired
    private KnowledgeDatasetService knowledgeDatasetService;
}
```

> **注意**：SDK 所有 Bean 使用 `knowledgeSdk` 前缀命名（如 `knowledgeSdkOkHttpClient`、`knowledgeSdkTokenManager`），不会与主项目的 Bean 命名冲突。

### 4. RSA 公钥（PEM 文件）说明

SDK 在 SSO 登录时需要对用户信息进行 RSA 加密。加密流程如下：

1. SDK 将 `username` 和 `email` 构建为 JSON：`{"username":"alice","email":"alice@example.com"}`
2. 使用 RSA 公钥对该 JSON 字符串进行加密
3. 将加密结果 Base64 编码后作为 `HTTP_USER_INFO` 字段发送到 SSO 登录接口

**PEM 文件位置**：SDK 内置了默认的 RSA 公钥文件 `src/main/resources/rsa_public_key.pem`，打包后位于 classpath 根路径下。

**使用自定义公钥**：如果你的知识库系统使用不同的 RSA 密钥对，可以通过以下步骤替换：

1. 将你的 RSA 公钥 PEM 文件放到项目的 `src/main/resources/` 目录下（如 `my_rsa_key.pem`）
2. 在 `application.yml` 中配置：
   ```yaml
   knowledge:
     rsa-public-key-path: my_rsa_key.pem
   ```

PEM 文件格式示例：
```
-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC...（Base64 编码的公钥）
-----END PUBLIC KEY-----
```

> **注意**：PEM 文件必须是 X.509 格式的 RSA 公钥（`-----BEGIN PUBLIC KEY-----`），SDK 会自动忽略头尾标记行，解码中间的 Base64 内容。

---

## 使用场景示例

以下按照需求文档中的 9 个场景，逐一给出详细用法示例。

### 场景 1：创建知识库

手动创建一个知识库，返回知识库信息（包含 ID 和名称）。`name` 参数可以是任意名称，SDK 不做限制。如果需要创建公共或个人知识库，建议使用 `uploadToPublicDataset` 或 `uploadToUserDataset` 方法，它们会自动按照约定命名。

```java
import com.knowledge.sdk.model.DatasetResponse;
import com.knowledge.sdk.service.KnowledgeDatasetService;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeDatasetService knowledgeDatasetService;

    /**
     * 场景1：创建知识库
     */
    @PostMapping("/datasets")
    public DatasetResponse createDataset(@RequestParam String name) {
        // 创建名为 name 的知识库，返回包含 id、name 等信息的 DatasetResponse
        DatasetResponse dataset = knowledgeDatasetService.createDataset(name);
        System.out.println("知识库已创建, ID: " + dataset.getId() + ", 名称: " + dataset.getName());
        return dataset;
    }
}
```

### 场景 2：上传文档

上传单个文档到指定知识库。知识库通过名称指定，如果不存在会自动创建。

```java
import com.knowledge.sdk.service.KnowledgeDatasetService;
import org.springframework.web.multipart.MultipartFile;

/**
 * 场景2：上传单个文档到指定知识库
 */
@PostMapping("/datasets/{datasetName}/documents")
public List<String> uploadDocument(@PathVariable String datasetName,
                                   @RequestParam("file") MultipartFile file) {
    // 将单个文件封装为 List 后上传
    List<MultipartFile> files = Collections.singletonList(file);
    List<String> results = knowledgeDatasetService.uploadDocuments(datasetName, files);
    // results 包含 datasetId 或 batch 信息
    return results;
}
```

### 场景 3：批量上传文档

批量上传多个文档，SDK 会自动按每批最多 10 个文件进行分批上传。例如 23 个文件会自动分为 3 批（10 + 10 + 3）。

```java
/**
 * 场景3：批量上传文档（自动分批）
 * 
 * 文件上传限制：
 * - 单次最多 10 个文件（超过自动分批）
 * - 单个文件最大 20MB
 */
@PostMapping("/datasets/{datasetName}/documents/batch")
public List<String> batchUploadDocuments(@PathVariable String datasetName,
                                         @RequestParam("files") List<MultipartFile> files) {
    // SDK 自动分批上传
    // 例如：23 个文件 -> 3 批 (10 + 10 + 3)
    List<String> results = knowledgeDatasetService.uploadDocuments(datasetName, files);
    System.out.println("上传完成，共 " + results.size() + " 批");
    return results;
}
```

### 场景 4：上传文档时自动创建知识库

调用 `uploadDocuments` 时，如果指定的知识库不存在，SDK 会自动创建该知识库并上传文档，无需手动调用 `createDataset`。

```java
/**
 * 场景4：上传文档时自动创建知识库
 * 
 * 如果知识库 "product_docs" 不存在，SDK 会自动创建后再上传文档
 */
@PostMapping("/products/documents")
public List<String> uploadProductDocs(@RequestParam("files") List<MultipartFile> files) {
    // 如果 "product_docs" 知识库不存在，SDK 自动创建
    // 如果已存在，直接上传到该知识库
    List<String> results = knowledgeDatasetService.uploadDocuments("product_docs", files);
    return results;
}
```

### 场景 5：上传到个人知识库

上传文档到用户个人知识库，知识库名称自动按 `user_{userId}` 规则命名。如果用户的知识库不存在，SDK 自动创建。

> **知识库命名规则**：
> - 公共知识库：名称固定为 `public_dataset`（可通过 `knowledge.public-dataset-name` 修改）
> - 个人知识库：名称为 `user_{userId}`，如 `user_alice001`（前缀可通过 `knowledge.user-dataset-prefix` 修改）
>
> **知识库归属说明**：
> - **方式一（默认 token）**：使用配置文件中 `username/email` 的 token 创建知识库。知识库在知识库系统中归属于该默认用户，`user_{userId}` 只是命名约定用于区分不同用户的数据。所有个人知识库实际上都在同一个默认账户下管理。
> - **方式二（用户专属 token）**：以指定用户的 `username/email` 通过 SSO 登录获取专属 token，用该 token 创建的知识库在知识库系统中归属于该用户账户。这样每个用户的知识库是真正私有的，仅该用户可管理。

**方式一：使用默认 token（知识库在默认用户账户下，按命名区分）**

```java
/**
 * 使用配置文件中的默认用户 token 上传
 */
@PostMapping("/users/{userId}/documents")
public List<String> uploadToUserDataset(@PathVariable String userId,
                                         @RequestParam("files") List<MultipartFile> files) {
    List<String> results = knowledgeDatasetService.uploadToUserDataset(userId, files);
    return results;
}
```

**方式二：使用用户专属 token（知识库归属该用户账户，真正私有）**

不同用户登录后拿到不同的 token，传不同的 token 创建知识库时，知识库会归属于对应用户。

```java
/**
 * 场景5：上传到个人知识库（按用户身份登录）
 *
 * SDK 会以该用户的 username/email 通过 SSO 登录获取专属 token，
 * 用该 token 创建/上传知识库，知识库归属于该用户账户。
 *
 * HTTP_USER_INFO 数据格式：{"username": "alice", "email": "alice@example.com"}
 */
@PostMapping("/users/{userId}/documents")
public List<String> uploadToUserDataset(@PathVariable String userId,
                                         @RequestParam String username,
                                         @RequestParam String email,
                                         @RequestParam("files") List<MultipartFile> files) {
    // SDK 以 alice 的身份登录，获取 alice 的 token
    // 用 alice 的 token 创建知识库 "user_alice001"，该知识库归属于 alice
    List<String> results = knowledgeDatasetService.uploadToUserDataset(
            userId, username, email, files);
    System.out.println("已上传到用户 " + userId + " 的个人知识库（使用用户专属 token）");
    return results;
}
```

### 场景 6：上传到公共知识库

上传文档到公共知识库（默认名称 `public_dataset`，可通过配置修改）。

```java
/**
 * 场景6：上传到公共知识库
 * 
 * 公共知识库默认名称：public_dataset
 * 可通过 knowledge.public-dataset-name 配置修改
 */
@PostMapping("/public/documents")
public List<String> uploadToPublicDataset(@RequestParam("files") List<MultipartFile> files) {
    // 上传到公共知识库 "public_dataset"
    List<String> results = knowledgeDatasetService.uploadToPublicDataset(files);
    System.out.println("已上传到公共知识库");
    return results;
}
```

### 场景 7：删除文档

从指定知识库中删除单个文档。

```java
/**
 * 场景7：删除文档
 */
@DeleteMapping("/datasets/{datasetId}/documents/{documentId}")
public void deleteDocument(@PathVariable String datasetId,
                           @PathVariable String documentId) {
    knowledgeDatasetService.deleteDocument(datasetId, documentId);
    System.out.println("文档已删除: " + documentId);
}
```

### 场景 8：删除知识库

删除整个知识库及其所有文档。

```java
/**
 * 场景8：删除知识库
 */
@DeleteMapping("/datasets/{datasetId}")
public void deleteDataset(@PathVariable String datasetId) {
    knowledgeDatasetService.deleteDataset(datasetId);
    System.out.println("知识库已删除: " + datasetId);
}
```

### 场景 9：登录（SDK 自动处理）

SDK 内部通过 SSO 自动完成登录和 Token 管理，业务代码**无需手动处理**。

SDK 自动处理以下逻辑：
- 首次调用接口时自动通过 SSO 登录获取 `access_token`
- Token 缓存，未过期时复用
- Token 过期前自动刷新（提前 `tokenExpiryBufferSeconds` 秒刷新）
- 接口返回 `401` 时自动重新登录并重试请求
- **支持多用户 token**：每个用户的 token 独立缓存和管理

SSO 登录请求中的 `HTTP_USER_INFO` 数据格式为：
```json
{"username": "alice", "email": "alice@example.com"}
```

该 JSON 会经过 RSA 加密后发送到 SSO 接口。

```java
/**
 * 场景9：登录 — 业务代码无需关心
 *
 * 只需配置 application.yml：
 *   knowledge.base-url: https://your-host
 *   knowledge.system-token: your-system-token
 *   knowledge.username: your-username
 *   knowledge.email: your-email
 *
 * SDK 会自动调用 SSO 登录接口获取 token：
 *   POST /tenant/api/app/account/sso_login
 *   Authorization: Bearer {systemToken}
 *   Body: { "HTTP_USER_INFO": "{rsaEncrypted({\"username\":\"...\",\"email\":\"...\"})}" }
 *
 * 默认用户使用配置文件中的 username/email。
 * 也支持按用户身份登录（见场景5），每个用户有独立的 token 缓存。
 */
@PostMapping("/example")
public List<String> example(@RequestParam("files") List<MultipartFile> files) {
    // 直接调用，SDK 自动处理登录和 token
    return knowledgeDatasetService.uploadToPublicDataset(files);
}
```

---

## 完整 Controller 示例

以下是一个包含所有场景的完整 Controller 示例：

```java
package com.example.controller;

import com.knowledge.sdk.exception.KnowledgeException;
import com.knowledge.sdk.model.DatasetResponse;
import com.knowledge.sdk.service.KnowledgeDatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeDatasetService knowledgeDatasetService;

    // 场景1：创建知识库
    @PostMapping("/datasets")
    public ResponseEntity<DatasetResponse> createDataset(@RequestParam String name) {
        DatasetResponse dataset = knowledgeDatasetService.createDataset(name);
        return ResponseEntity.ok(dataset);
    }

    // 场景2：上传单个文档
    @PostMapping("/datasets/{datasetName}/document")
    public ResponseEntity<List<String>> uploadDocument(
            @PathVariable String datasetName,
            @RequestParam("file") MultipartFile file) {
        List<String> results = knowledgeDatasetService.uploadDocuments(
                datasetName, Collections.singletonList(file));
        return ResponseEntity.ok(results);
    }

    // 场景3：批量上传文档（自动分批）
    @PostMapping("/datasets/{datasetName}/documents")
    public ResponseEntity<List<String>> batchUpload(
            @PathVariable String datasetName,
            @RequestParam("files") List<MultipartFile> files) {
        List<String> results = knowledgeDatasetService.uploadDocuments(datasetName, files);
        return ResponseEntity.ok(results);
    }

    // 场景4：上传时自动创建知识库（同场景2/3，知识库不存在时自动创建）

    // 场景5：上传到个人知识库（默认 token）
    @PostMapping("/users/{userId}/documents")
    public ResponseEntity<List<String>> uploadToUser(
            @PathVariable String userId,
            @RequestParam("files") List<MultipartFile> files) {
        List<String> results = knowledgeDatasetService.uploadToUserDataset(userId, files);
        return ResponseEntity.ok(results);
    }

    // 场景5（扩展）：上传到个人知识库（用户专属 token，知识库归属该用户）
    @PostMapping("/users/{userId}/documents/as-user")
    public ResponseEntity<List<String>> uploadToUserAsUser(
            @PathVariable String userId,
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam("files") List<MultipartFile> files) {
        List<String> results = knowledgeDatasetService.uploadToUserDataset(
                userId, username, email, files);
        return ResponseEntity.ok(results);
    }

    // 场景6：上传到公共知识库
    @PostMapping("/public/documents")
    public ResponseEntity<List<String>> uploadToPublic(
            @RequestParam("files") List<MultipartFile> files) {
        List<String> results = knowledgeDatasetService.uploadToPublicDataset(files);
        return ResponseEntity.ok(results);
    }

    // 场景7：删除文档
    @DeleteMapping("/datasets/{datasetId}/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable String datasetId,
            @PathVariable String documentId) {
        knowledgeDatasetService.deleteDocument(datasetId, documentId);
        return ResponseEntity.noContent().build();
    }

    // 场景8：删除知识库
    @DeleteMapping("/datasets/{datasetId}")
    public ResponseEntity<Void> deleteDataset(@PathVariable String datasetId) {
        knowledgeDatasetService.deleteDataset(datasetId);
        return ResponseEntity.noContent().build();
    }

    // 场景9：登录 — SDK 自动处理，无需编写代码
}
```

## 异常处理

SDK 使用统一异常 `KnowledgeException`，可在业务代码中捕获处理：

```java
import com.knowledge.sdk.exception.KnowledgeException;

@PostMapping("/upload")
public ResponseEntity<?> upload(@RequestParam("files") List<MultipartFile> files) {
    try {
        List<String> results = knowledgeDatasetService.uploadToPublicDataset(files);
        return ResponseEntity.ok(results);
    } catch (KnowledgeException e) {
        // e.getStatusCode() 可获取 HTTP 状态码（如 401、500）
        // e.getMessage() 获取错误描述
        return ResponseEntity.status(500).body("知识库操作失败: " + e.getMessage());
    }
}
```

异常场景包括：
- Token 获取失败
- HTTP 接口调用失败
- 文件大小超过限制（默认 20MB）
- 文件列表为空

## 项目结构

```
com.knowledge.sdk
├── auth/
│   └── TokenManager.java          # Token 管理（SSO 登录、缓存、自动刷新）
├── client/
│   └── KnowledgeHttpClient.java   # HTTP 客户端（OkHttp 封装、请求重试）
├── config/
│   ├── KnowledgeAutoConfiguration.java  # Spring 自动配置
│   └── KnowledgeProperties.java         # 配置属性
├── exception/
│   └── KnowledgeException.java    # 统一异常
├── model/
│   ├── DatasetResponse.java       # 知识库响应
│   ├── DatasetListResponse.java   # 知识库列表响应
│   ├── DocumentResponse.java      # 文档响应
│   └── FileUploadResponse.java    # 文件上传响应
└── service/
    └── KnowledgeDatasetService.java  # 核心服务（业务入口）
```