package io.gitee.lujianshi.bidwinningbackend.knowledgeingestionagent.client;

import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.knowledgeingestionagent.client.dto.KnowledgeIngestionJobRequest;
import io.gitee.lujianshi.bidwinningbackend.knowledgeingestionagent.client.dto.KnowledgeIngestionJobResponse;
import io.gitee.lujianshi.bidwinningbackend.knowledgeingestionagent.config.KnowledgeIngestionAgentProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Java调用Python政策资讯采集服务的唯一HTTP客户端。 */
@Component
public class KnowledgeIngestionAgentClient {

    private final RestClient restClient;

    public KnowledgeIngestionAgentClient(
            RestClient.Builder restClientBuilder,
            KnowledgeIngestionAgentProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getPythonBaseUrl())
                .build();
    }

    /** 启动一次异步政策资讯采集任务。 */
    public KnowledgeIngestionJobResponse startJob(
            KnowledgeIngestionJobRequest request) {
        try {
            KnowledgeIngestionJobResponse response = restClient.post()
                    .uri("/policy-ingestion/jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(KnowledgeIngestionJobResponse.class);
            return requireResponse(response, "启动政策资讯采集任务");
        } catch (RestClientException e) {
            throw pythonCallFailed("启动政策资讯采集任务", e);
        }
    }

    /** 查询Python侧政策资讯采集任务状态。 */
    public KnowledgeIngestionJobResponse getJob(String jobId) {
        try {
            KnowledgeIngestionJobResponse response = restClient.get()
                    .uri("/policy-ingestion/jobs/{jobId}", jobId)
                    .retrieve()
                    .body(KnowledgeIngestionJobResponse.class);
            return requireResponse(response, "查询政策资讯采集任务");
        } catch (RestClientException e) {
            throw pythonCallFailed("查询政策资讯采集任务", e);
        }
    }

    private KnowledgeIngestionJobResponse requireResponse(
            KnowledgeIngestionJobResponse response,
            String operation) {
        if (response == null) {
            throw pythonCallFailed(operation + "返回为空", null);
        }
        return response;
    }

    private AppException pythonCallFailed(String message, Throwable cause) {
        return new AppException(
                AppErrorCode.KNOWLEDGE_INGESTION_AGENT_PYTHON_CALL_FAILED,
                "knowledge-ingestion-agent",
                "KNOWLEDGE_INGESTION_AGENT",
                "PYTHON_CALL",
                message,
                cause);
    }
}
