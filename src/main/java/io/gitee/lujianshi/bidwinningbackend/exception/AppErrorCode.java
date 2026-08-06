package io.gitee.lujianshi.bidwinningbackend.exception;

/**
 * 应用层结构化错误码。
 *
 * <p>所有模块共用一个枚举，通过错误码字符串前缀进行分类。错误码负责表达稳定的业务/系统语义，
 * HTTP 状态码负责表达 HTTP 层结果；具体操作由异常中的 operation 和 stage 描述。</p>
 */
public enum AppErrorCode {

    // ==================== COMMON：请求级错误 ====================

    /** COMMON：通用请求参数不合法。 */
    COMMON_INVALID_ARGUMENT(
            "COMMON.INVALID_ARGUMENT", 400, "请求参数不合法", false),

    /** COMMON：通用请求内容无法解析。 */
    COMMON_REQUEST_PARSE_FAILED(
            "COMMON.REQUEST_PARSE_FAILED", 400, "请求内容无法解析", false),

    /** COMMON：上传内容超过服务配置限制。 */
    COMMON_FILE_TOO_LARGE(
            "COMMON.FILE_TOO_LARGE", 413, "文件大小超过限制", false),

    /** COMMON：通用资源不存在。 */
    COMMON_NOT_FOUND(
            "COMMON.NOT_FOUND", 404, "资源不存在", false),

    // ==================== DATABASE：数据库错误 ====================

    /** DATABASE：无法归属具体业务模块的数据库操作失败。 */
    DATABASE_OPERATION_FAILED(
            "DATABASE.OPERATION_FAILED", 500, "数据库操作失败", true),

    /** DATABASE：数据库约束校验失败。 */
    DATABASE_CONSTRAINT_VIOLATION(
            "DATABASE.CONSTRAINT_VIOLATION", 409, "数据约束校验失败", false),

    /** DATABASE：数据库唯一键冲突。 */
    DATABASE_DUPLICATE_KEY(
            "DATABASE.DUPLICATE_KEY", 409, "数据已存在或发生并发冲突", true),

    /** DATABASE：数据库连接或数据库资源不可用。 */
    DATABASE_CONNECTION_FAILED(
            "DATABASE.CONNECTION_FAILED", 503, "数据库连接失败", true),

    /** DATABASE：数据库查询执行超时。 */
    DATABASE_QUERY_TIMEOUT(
            "DATABASE.QUERY_TIMEOUT", 504, "数据库操作超时", true),

    // ==================== BID_RECORD：投标记录 ====================

    /** BID_RECORD：投标记录不存在。 */
    BID_RECORD_NOT_FOUND(
            "BID_RECORD.NOT_FOUND", 404, "投标记录不存在", false),

    /** BID_RECORD：投标记录持久化失败。 */
    BID_RECORD_PERSISTENCE_FAILED(
            "BID_RECORD.PERSISTENCE_FAILED", 500, "投标记录保存失败", true),

    /** BID_RECORD：投标序号生成发生并发冲突。 */
    BID_RECORD_BID_NO_CONFLICT(
            "BID_RECORD.BID_NO_CONFLICT", 409, "投标序号生成发生冲突", true),

    /** BID_RECORD：当前账号无权操作该投标记录。 */
    BID_RECORD_FORBIDDEN(
            "BID_RECORD.FORBIDDEN", 403, "无权操作该投标记录", false),

    // ==================== BIDDER_ACCOUNT：投标方账号 ====================

    /** BIDDER_ACCOUNT：登录账号名已被占用。 */
    BIDDER_ACCOUNT_USERNAME_TAKEN(
            "BIDDER_ACCOUNT.USERNAME_TAKEN", 409, "账号名已存在", false),

    /** BIDDER_ACCOUNT：账号或密码错误。 */
    BIDDER_ACCOUNT_INVALID_CREDENTIALS(
            "BIDDER_ACCOUNT.INVALID_CREDENTIALS", 401, "账号或密码错误", false),

    /** BIDDER_ACCOUNT：未登录或登录已失效。 */
    BIDDER_ACCOUNT_UNAUTHORIZED(
            "BIDDER_ACCOUNT.UNAUTHORIZED", 401, "请先登录", false),

    // ==================== BID_INVITER_ACCOUNT：招标方账号 ====================

    /** BID_INVITER_ACCOUNT：登录账号名已被占用。 */
    BID_INVITER_ACCOUNT_USERNAME_TAKEN(
            "BID_INVITER_ACCOUNT.USERNAME_TAKEN", 409, "账号名已存在", false),

    /** BID_INVITER_ACCOUNT：账号或密码错误。 */
    BID_INVITER_ACCOUNT_INVALID_CREDENTIALS(
            "BID_INVITER_ACCOUNT.INVALID_CREDENTIALS", 401, "账号或密码错误", false),

    /** BID_INVITER_ACCOUNT：未登录或登录已失效。 */
    BID_INVITER_ACCOUNT_UNAUTHORIZED(
            "BID_INVITER_ACCOUNT.UNAUTHORIZED", 401, "请先登录", false),

    // ==================== BID_HANDLER：招标经办人 ====================

    /** BID_HANDLER：招标经办人不存在。 */
    BID_HANDLER_NOT_FOUND(
            "BID_HANDLER.NOT_FOUND", 404, "招标经办人不存在", false),

    /** BID_HANDLER：招标经办人持久化失败。 */
    BID_HANDLER_PERSISTENCE_FAILED(
            "BID_HANDLER.PERSISTENCE_FAILED", 500, "招标经办人保存失败", true),

    // ==================== INVITATION：招标公告 ====================

    /** INVITATION：招标公告不存在。 */
    INVITATION_NOT_FOUND(
            "INVITATION.NOT_FOUND", 404, "招标公告不存在", false),

    /** INVITATION：招标公告持久化失败。 */
    INVITATION_PERSISTENCE_FAILED(
            "INVITATION.PERSISTENCE_FAILED", 500, "招标公告保存失败", true),

    /** INVITATION：招标公告状态跳转不合法。 */
    INVITATION_INVALID_STATUS_TRANSITION(
            "INVITATION.INVALID_STATUS_TRANSITION", 400, "招标公告状态变更不合法", false),

    /** INVITATION：招标公告状态发生并发冲突。 */
    INVITATION_STATUS_CONFLICT(
            "INVITATION.STATUS_CONFLICT", 409, "招标公告状态已发生变化", true),

    // ==================== CREDENTIAL_FILE：资信文件 ====================

    /** CREDENTIAL_FILE：上传请求不合法。 */
    CREDENTIAL_FILE_INVALID_UPLOAD_REQUEST(
            "CREDENTIAL_FILE.INVALID_UPLOAD_REQUEST", 400, "资信文件上传请求不合法", false),

    /** CREDENTIAL_FILE：上传文件不合法。 */
    CREDENTIAL_FILE_INVALID_FILE(
            "CREDENTIAL_FILE.INVALID_FILE", 400, "上传文件不合法", false),

    /** CREDENTIAL_FILE：文件路径不合法。 */
    CREDENTIAL_FILE_INVALID_PATH(
            "CREDENTIAL_FILE.INVALID_PATH", 400, "文件路径不合法", false),

    /** CREDENTIAL_FILE：文件存储失败。 */
    CREDENTIAL_FILE_STORAGE_FAILED(
            "CREDENTIAL_FILE.STORAGE_FAILED", 503, "资信文件存储失败", true),

    /** CREDENTIAL_FILE：文件完整性校验失败。 */
    CREDENTIAL_FILE_INTEGRITY_CHECK_FAILED(
            "CREDENTIAL_FILE.INTEGRITY_CHECK_FAILED", 500, "文件完整性校验失败", true),

    /** CREDENTIAL_FILE：资信文件数据库持久化失败。 */
    CREDENTIAL_FILE_PERSISTENCE_FAILED(
            "CREDENTIAL_FILE.PERSISTENCE_FAILED", 500, "资信文件保存失败", true),

    /** CREDENTIAL_FILE：资信文件转换失败。 */
    CREDENTIAL_FILE_CONVERSION_FAILED(
            "CREDENTIAL_FILE.CONVERSION_FAILED", 502, "资信文件转换失败", true),

    /** CREDENTIAL_FILE：转换批次与文件数据不一致。 */
    CREDENTIAL_FILE_CONVERSION_DATA_INCONSISTENT(
            "CREDENTIAL_FILE.CONVERSION_DATA_INCONSISTENT", 500, "资信文件转换数据不一致", false),

    /** CREDENTIAL_FILE：文件或归属记录不存在。 */
    CREDENTIAL_FILE_NOT_FOUND(
            "CREDENTIAL_FILE.NOT_FOUND", 404, "资信文件不存在", false),

    // ==================== KNOWLEDGE_FILE：知识文件 ====================

    /** KNOWLEDGE_FILE：多文件上传请求不合法。 */
    KNOWLEDGE_FILE_INVALID_UPLOAD_REQUEST(
            "KNOWLEDGE_FILE.INVALID_UPLOAD_REQUEST", 400, "知识文件上传请求不合法", false),

    /** KNOWLEDGE_FILE：单个上传文件不合法。 */
    KNOWLEDGE_FILE_INVALID_FILE(
            "KNOWLEDGE_FILE.INVALID_FILE", 400, "知识文件不合法", false),

    /** KNOWLEDGE_FILE：对象存储读写失败。 */
    KNOWLEDGE_FILE_STORAGE_FAILED(
            "KNOWLEDGE_FILE.STORAGE_FAILED", 503, "知识文件存储失败", true),

    /** KNOWLEDGE_FILE：对象正文回读哈希不一致。 */
    KNOWLEDGE_FILE_INTEGRITY_CHECK_FAILED(
            "KNOWLEDGE_FILE.INTEGRITY_CHECK_FAILED", 500, "知识文件完整性校验失败", true),

    /** KNOWLEDGE_FILE：original 转 Markdown 失败。 */
    KNOWLEDGE_FILE_CONVERSION_FAILED(
            "KNOWLEDGE_FILE.CONVERSION_FAILED", 502, "知识文件转换失败", true),

    /** KNOWLEDGE_FILE：chat-agent 向量化或补偿删除失败。 */
    KNOWLEDGE_FILE_VECTORIZATION_FAILED(
            "KNOWLEDGE_FILE.VECTORIZATION_FAILED", 502, "知识文件向量化失败", true),

    // ==================== CHAT_AGENT：知识问答 ====================

    /** CHAT_AGENT：知识问答服务调用或响应校验失败。 */
    CHAT_AGENT_PYTHON_CALL_FAILED(
            "CHAT_AGENT.PYTHON_CALL_FAILED", 502, "知识问答服务调用失败", true),

    /** CHAT_AGENT：会话不存在。 */
    CHAT_AGENT_CONVERSATION_NOT_FOUND(
            "CHAT_AGENT.CONVERSATION_NOT_FOUND", 404, "会话不存在", false),

    /** CHAT_AGENT：当前用户无权访问该会话。 */
    CHAT_AGENT_CONVERSATION_FORBIDDEN(
            "CHAT_AGENT.CONVERSATION_FORBIDDEN", 403, "无权访问该会话", false),

    /** CHAT_AGENT：同一客户端消息 ID 被用于提交不同问题。 */
    CHAT_AGENT_IDEMPOTENCY_CONFLICT(
            "CHAT_AGENT.IDEMPOTENCY_CONFLICT", 409, "客户端消息 ID 与已有请求内容不一致", false),

    // ==================== RISK_ANALYSIS：风险分析 ====================

    /** RISK_ANALYSIS：风险分析输入材料未准备完成。 */
    RISK_ANALYSIS_INPUT_NOT_READY(
            "RISK_ANALYSIS.INPUT_NOT_READY", 400, "风险分析输入材料未准备完成", false),

    /** RISK_ANALYSIS：当前投标记录正在分析。 */
    RISK_ANALYSIS_IN_PROGRESS(
            "RISK_ANALYSIS.IN_PROGRESS", 409, "风险分析正在进行中", true),

    /** RISK_ANALYSIS：Markdown 材料汇总失败。 */
    RISK_ANALYSIS_MARKDOWN_AGGREGATION_FAILED(
            "RISK_ANALYSIS.MARKDOWN_AGGREGATION_FAILED", 500, "风险分析材料汇总失败", true),

    /** RISK_ANALYSIS：大模型服务调用失败。 */
    RISK_ANALYSIS_LLM_CALL_FAILED(
            "RISK_ANALYSIS.LLM_CALL_FAILED", 502, "风险分析模型调用失败", true),

    /** RISK_ANALYSIS：大模型返回内容不符合预期。 */
    RISK_ANALYSIS_INVALID_LLM_RESPONSE(
            "RISK_ANALYSIS.INVALID_LLM_RESPONSE", 502, "风险分析模型返回内容无效", true),

    /** RISK_ANALYSIS：风险分析结果持久化失败。 */
    RISK_ANALYSIS_PERSISTENCE_FAILED(
            "RISK_ANALYSIS.PERSISTENCE_FAILED", 500, "风险分析结果保存失败", true),

    // ==================== MAIL：邮件通知 ====================

    /** MAIL：邮件发送失败。 */
    MAIL_SEND_FAILED(
            "MAIL.SEND_FAILED", 502, "邮件发送失败", true),

    // ==================== SYSTEM：系统级错误 ====================

    /** SYSTEM：系统内部未分类异常。 */
    SYSTEM_INTERNAL_ERROR(
            "SYSTEM.INTERNAL_ERROR", 500, "服务器内部错误", false),

    /** SYSTEM：系统依赖服务不可用。 */
    SYSTEM_DEPENDENCY_UNAVAILABLE(
            "SYSTEM.DEPENDENCY_UNAVAILABLE", 503, "系统依赖服务不可用", true);

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;
    private final boolean retryable;

    AppErrorCode(
            String code,
            int httpStatus,
            String defaultMessage,
            boolean retryable) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
        this.retryable = retryable;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
