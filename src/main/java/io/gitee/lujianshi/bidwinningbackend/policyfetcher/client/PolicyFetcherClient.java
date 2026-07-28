package io.gitee.lujianshi.bidwinningbackend.policyfetcher.client;

import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.policyfetcher.client.dto.IngestionJobRequest;
import io.gitee.lujianshi.bidwinningbackend.policyfetcher.client.dto.IngestionJobResponse;
import io.gitee.lujianshi.bidwinningbackend.policyfetcher.config.IngestionProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Java调用Python政策资讯采集服务的唯一HTTP客户端。 */
@Component
public class PolicyFetcherClient {

    private final RestClient restClient;

    public PolicyFetcherClient(
            RestClient.Builder restClientBuilder,
            IngestionProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getPythonBaseUrl())
                .build();
    }

    /** 启动一次异步政策资讯采集任务。 */
    public IngestionJobResponse startJob(
            IngestionJobRequest request) {
        try {
            IngestionJobResponse response = restClient.post()
                    .uri("/policy-ingestion/jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(IngestionJobResponse.class);
            return requireResponse(response, "启动政策资讯采集任务");
        } catch (RestClientException e) {
            throw pythonCallFailed("启动政策资讯采集任务", e);
        }
    }

    /** 查询Python侧政策资讯采集任务状态。 */
    public IngestionJobResponse getJob(String jobId) {
        try {
            IngestionJobResponse response = restClient.get()
                    .uri("/policy-ingestion/jobs/{jobId}", jobId)
                    .retrieve()
                    .body(IngestionJobResponse.class);
            return requireResponse(response, "查询政策资讯采集任务");
        } catch (RestClientException e) {
            throw pythonCallFailed("查询政策资讯采集任务", e);
        }
    }

    private IngestionJobResponse requireResponse(
            IngestionJobResponse response,
            String operation) {
        if (response == null) {
            throw pythonCallFailed(operation + "返回为空", null);
        }
        return response;
    }

    private AppException pythonCallFailed(String message, Throwable cause) {
        return new AppException(
                AppErrorCode.POLICY_FETCHER_PYTHON_CALL_FAILED,
                "policy-fetcher",
                "POLICY_FETCHER",
                "PYTHON_CALL",
                message,
                cause);
    }
}
