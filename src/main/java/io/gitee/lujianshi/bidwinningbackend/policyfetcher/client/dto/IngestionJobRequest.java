package io.gitee.lujianshi.bidwinningbackend.policyfetcher.client.dto;

import java.time.LocalDateTime;

/** 启动一次政策资讯采集任务的请求体。 */
public record IngestionJobRequest(
        String jobId,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        String callbackUrl) {
}
