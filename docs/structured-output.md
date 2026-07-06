# Structured Output 链路笔记

> 适用版本:Spring AI 2.0.0 + LM Studio(OpenAI 兼容 `/v1/chat/completions`)+ 本地小模型(如 `google/gemma-4-e4b`)
>
> 本项目落地位置:`RiskAnalysisService.java:41-78`

---

## 1. 问题背景

本地小模型常带"思考过程"输出,导致响应中混入 reasoning 文本或 ```` ```json ```` 围栏。后端 `JSON.parseObject(raw)` 失败 → 三字段为 null → DB 写入撞 `risk_level` NOT NULL 约束。

**根因:之前只在 prompt 里写"只返回 JSON,不要包含任何其他文字说明",这是软约束,小模型不一定遵守。** 需要在采样阶段物理拦截,让模型根本无法生成非 JSON 字符。

---

## 2. Spring AI 官方文档摘要

[Spring AI 2.0 — OpenAI Chat / Structured Outputs](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html#_structured_outputs) 关键点:

| 字段 | 含义 |
|---|---|
| `ResponseFormat.Type.TEXT` | 默认,纯文本 |
| `ResponseFormat.Type.JSON_OBJECT` | 启用 JSON 模式,保证输出是合法 JSON(不约束结构) |
| `ResponseFormat.Type.JSON_SCHEMA` | 启用 Structured Outputs,保证输出匹配用户提供的 JSON Schema |

配置方式两种:
- **application.yml / .properties**:`spring.ai.openai.chat.response-format.{type,name,strict,schema}`
- **代码 builder**:`OpenAiChatOptions.builder().responseFormat(new ResponseFormat(Type, schema))`

**本项目坑记录:** 本地 YAML 配置走不通(schema 绑成空字符串),改用代码 builder 才生效。

---

## 3. 链路总览

```
┌────────────────────────────────────────────────────────────────────────┐
│  ① Java 后端                                                          │
│  RiskAnalysisService.java                                              │
│  - RISK_SCHEMA 常量(Java text block)                                  │
│  - ResponseFormat.builder().type(JSON_SCHEMA).jsonSchema(SCHEMA).build()│
│  - ChatClient.builder(model).defaultOptions(opts).build()              │
│                                                                        │
│           ↓ chatClient.prompt().system(...).user(...).call().content() │
│                                                                        │
│  ② Spring AI 运行时                                                    │
│  - OpenAiChatOptions → JSON 序列化                                     │
│  - 构造 HTTP POST body:                                                │
│    {                                                                   │
│      "model": "...",                                                   │
│      "messages": [...],                                                │
│      "response_format": {                                              │
│        "type": "json_schema",                                          │
│        "json_schema": "{...the schema as string...}"                   │
│      }                                                                 │
│    }                                                                   │
│           ↓                                                            │
│                                                                        │
│  ③ HTTP 传输                                                           │
│  POST http://localhost:1234/v1/chat/completions                        │
│  Authorization: Bearer lm-studio                                       │
│           ↓                                                            │
│                                                                        │
│  ④ LM Studio server                                                    │
│  - 解析 OpenAI 兼容请求体                                              │
│  - 检测到 response_format.json_schema → 启动 grammar 编译器             │
│           ↓                                                            │
│                                                                        │
│  ⑤ llama.cpp 引擎                                                      │
│  - 把 JSON Schema 编译成 GBNF(EBNF 变种)语法                           │
│  - 把 GBNF 注入 sampling 循环                                          │
│           ↓                                                            │
│                                                                        │
│  ⑥ 大模型推理                                                          │
│  - 每生成一个 token 都通过 GBNF 验证器                                 │
│  - 非法 token 在 logit 阶段被 mask(概率置 0)                           │
│  - 模型根本无法"想"输出非法字符                                        │
│           ↓                                                            │
│                                                                        │
│  ⑦ LM Studio 返回 chat.completion                                     │
│  {                                                                     │
│    "choices": [{                                                       │
│      "message": {                                                      │
│        "content": "{\"riskLevel\":\"CRITICAL\",...}"                   │
│      }                                                                 │
│    }]                                                                  │
│  }                                                                     │
│           ↓                                                            │
│                                                                        │
│  ⑧ Spring AI 反序列化 → Java String                                    │
│  → RiskAnalysisService.parseAndBuild()                                 │
│  → JSON.parseObject(raw) → JSONObject                                  │
│  → RiskAnalysis 实体 → mapper.insert() → DB                            │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Step 1:Java 后端构造 schema 与 options

```java
// RiskAnalysisService.java:41-52
private static final String RISK_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "riskLevel":   {"type": "string", "enum": ["LOW", "MEDIUM", "HIGH", "CRITICAL"]},
            "summary":     {"type": "string"},
            "suggestions": {"type": "string"}
          },
          "required": ["riskLevel", "summary", "suggestions"],
          "additionalProperties": false
        }
        """;

// RiskAnalysisService.java:68-78
public RiskAnalysisService(OpenAiChatModel openAiChatModel) {
    ResponseFormat responseFormat = ResponseFormat.builder()
            .type(ResponseFormat.Type.JSON_SCHEMA)
            .jsonSchema(RISK_SCHEMA)
            .build();
    OpenAiChatOptions.Builder defaults = OpenAiChatOptions.builder()
            .responseFormat(responseFormat);
    this.chatClient = ChatClient.builder(openAiChatModel)
            .defaultOptions(defaults)
            .build();
}
```

### 关键 API 坑(对比官方文档示例)

| 官方文档示例 | 实际包路径 | 备注 |
|---|---|---|
| `import org.springframework.ai.openai.api.ResponseFormat` | `org.springframework.ai.openai.OpenAiChatModel.ResponseFormat` | **`ResponseFormat` 是 `OpenAiChatModel` 的内部类**,不是独立类 |
| `new ResponseFormat(Type.JSON_SCHEMA, schema)` | `ResponseFormat.builder().type(...).jsonSchema(schema).build()` | **只有 Builder,没有公共构造器** |
| 字段名 `schema` | 字段名 `jsonSchema` | camelCase,序列化时映射到 OpenAI `json_schema` |
| `defaultOptions(OpenAiChatOptions)` | `defaultOptions(OpenAiChatOptions.Builder)` | 接收 **Builder**,不是 build 后的对象 |

---

## 5. Step 2:Spring AI 序列化请求

`OpenAiChatOptions.responseFormat(...)` 在 Spring AI 内部被映射到 OpenAI 官方 wire format。实际 HTTP body 形如:

```json
{
  "model": "google/gemma-4-e4b",
  "messages": [
    {"role": "system", "content": "你是一位专业的政府采购风险分析师..."},
    {"role": "user", "content": "请分析以下投标人的风险情况..."}
  ],
  "response_format": {
    "type": "json_schema",
    "json_schema": "{\"type\":\"object\",\"properties\":...}"
  },
  "temperature": 0.7,
  "stream": false
}
```

**注意:** Spring AI 把 `ResponseFormat.jsonSchema` 字段直接以**字符串形式**嵌入到 `response_format.json_schema`。LM Studio 收到后再做二次 `JSON.parse` 还原成 schema 对象。

---

## 6. Step 3:LM Studio 接收并识别格式约束

[LM Studio Structured Output 文档](https://lmstudio.ai/docs/developer/openai-compat/structured-output):

> The API supports structured JSON outputs through the `/v1/chat/completions` endpoint when given a JSON schema. Doing this will cause the LLM to respond in valid JSON conforming to the schema provided.

LM Studio server 收到 `response_format.type == "json_schema"` 后:
1. 解析 `response_format.json_schema` 字符串为 JSON Schema 对象
2. 根据当前加载模型的引擎类型选择实现:
   - **GGUF 模型** → llama.cpp 的 grammar-based sampling(GBNF)
   - **MLX 模型** → [Outlines](https://github.com/dottxt-ai/outlines)
3. 触发 schema → grammar 编译
4. 把生成的 grammar 注入到 llama.cpp 的 sampling context

---

## 7. Step 4:GBNF Grammar 编译原理

**GBNF**(GGML BNF)是 llama.cpp 使用的语法格式,EBNF 的简化变种。

JSON Schema → GBNF 的大致转换规则(简化):

```gbnf
root ::= object
object ::= "{" ws "\"riskLevel\"" ws ":" ws string-enum "," ws "\"summary\"" ws ":" ws string "," ws "\"suggestions\"" ws ":" ws string "}"
string-enum ::= "\"LOW\"" | "\"MEDIUM\"" | "\"HIGH\"" | "\"CRITICAL\""
string ::= "\"" char* "\""
char ::= [^"\\] | "\\" escape
ws ::= [ \t\n]*
```

每一行都是 BNF 产生式,定义了合法 token 序列的有限状态机。

---

## 8. Step 5:大模型采样受限(核心)

**这是链路中最关键的一步。** 误解澄清:

| ❌ 不是这样 | ✅ 是这样 |
|---|---|
| prompt 约束 — 模型"被告知"不要输出 thinking | 采样阶段物理拦截 |
| 后处理 — 模型先生成任意文本,服务端再清洗 | 每个 token 生成前先过 grammar 验证 |
| 概率抑制 — 让 thinking 文本"概率变低" | 非法 token 在 logit 阶段被 mask,概率直接置 0 |

**具体机制:**

```
for each token to generate:
    logits = model.forward(input_ids)        # 模型给出所有 token 的概率
    
    # 关键:grammar sampler 介入
    valid_tokens = gbnf_grammar.get_valid_next_tokens(current_state)
    for t in all_tokens:
        if t not in valid_tokens:
            logits[t] = -inf                 # 物理 mask 掉非法 token
    
    # 然后才采样
    next_token = sample(logits)              # 只能从合法 token 里选
    state = gbnf_grammar.advance(state, next_token)
```

**效果:** 模型"想输出" `"Here's a thinking process...` 时,首字符 `H` 不在任何合法 GBNF 状态中(因为状态机期望 `{`),`logits["H"] = -inf`,采样永远选不到。这就是为什么实测 gemma-4-e4b 输出是纯 JSON,没有 thinking。

---

## 9. Step 6:返回与解析

LM Studio 生成结束后,把 token 流组装成字符串,塞回 OpenAI 兼容响应:

```json
{
  "id": "chatcmpl-y6x5cd6x881yil1bsn4dw",
  "object": "chat.completion",
  "model": "google/gemma-4-e4b",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "{\n  \"riskLevel\": \"CRITICAL\",\n  ...\n}",
      "reasoning_content": "",
      "tool_calls": []
    },
    "finish_reason": "stop"
  }]
}
```

Spring AI 反序列化 `content` 字段为 Java `String`,传入 `RiskAnalysisService.parseAndBuild`:

```
<<< [风险分析] AI 响应 (length=304, elapsed=6390ms, analyzedAt=...):
{
  "riskLevel": "CRITICAL",
  "summary": "...",
  "suggestions": "..."
}
>>> [风险分析] JSON 解析成功 riskLevel=CRITICAL
<<< [风险分析] 落库成功 analysisId=10
```

---

## 10. 关键调试手段

| 调试点 | 工具 |
|---|---|
| 看 LM Studio 收到什么请求 | `lms log stream`(终端开 stream) |
| 看 Spring AI 发出的 HTTP body | `logging.level.org.springframework.ai.openai=DEBUG` |
| 直接 curl LM Studio 验证 schema | 手动 POST 到 `http://localhost:1234/v1/chat/completions` 带 `response_format` |
| 看 model 是否支持 grammar | LM Studio → Developer → 看模型详情,小模型(< 7B)实测不可靠 |

---

## 11. 限制与替代方案

### 限制

1. **小模型支持不可靠** — LM Studio 官方明确警告"7B 以下模型可能不支持 Structured Output"。本项目 gemma-4-e4b 实测 OK,但不是所有小模型都行。
2. **JSON Schema 受支持子集** — 不是完整 JSON Schema。当前用的子集(`object` + `string` + `enum` + `required` + `additionalProperties:false`)是 OpenAI/LM Studio 都保证支持的。复杂特性(`$ref`、`oneOf`、`pattern`)部分支持。
3. **prompt 强化仍是必要的纵深防御** — `risk-analysis-system-v1.txt` 末尾追加的"输出格式(严格)"段保留,作为 schema 失败时的 fallback。

### 替代方案(Layer 2 fallback)

如果 Structured Output 在某些模型上仍失败,后端可以做二次校验:
- 多策略 JSON 提取:`JSON.parseObject(raw)` → 剥离 ```` ```json ```` 围栏 → 找首个 `{` 到末个 `}` 的子串
- 提取后字段缺失/枚举非法 → 抛 `RiskAnalysisParseException` → 502 给前端
- 这是 plan mode 阶段讨论过的方案,本项目暂不实现(因为 Layer 1 已经稳定工作)

---

## 引用

- Spring AI 2.0 OpenAI Chat: https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html
- LM Studio Structured Output: https://lmstudio.ai/docs/developer/openai-compat/structured-output
- LM Studio Chat Completions: https://lmstudio.ai/docs/developer/openai-compat/chat-completions
- OpenAI Structured Outputs(参考 schema 子集): https://platform.openai.com/docs/guides/structured-outputs
- llama.cpp GBNF: https://github.com/ggerganov/llama.cpp/blob/master/grammars/README.md
- Outlines(MLX 引擎后端): https://github.com/dottxt-ai/outlines