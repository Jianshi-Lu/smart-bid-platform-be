package io.gitee.lujianshi.bidwinningbackend.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件状态 SSE 发布器。
 *
 * <p>浏览器只接收事件，不向服务端发送业务消息；业务状态的事实源仍然是数据库，
 * SSE 事件只负责通知前端重新读取当前列表。</p>
 */
@Component
public class FileStatusEventPublisher {

    public static final String CREDENTIAL_TOPIC_PREFIX = "credential:";
    public static final String KNOWLEDGE_TOPIC = "knowledge";

    private static final Logger log = LoggerFactory.getLogger(
            FileStatusEventPublisher.class);

    private final Map<String, Set<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    /** 订阅一个主题；0 表示由应用自行管理连接生命周期。 */
    public SseEmitter subscribe(String topic) {
        SseEmitter emitter = new SseEmitter(0L);
        Set<SseEmitter> topicEmitters = emitters.computeIfAbsent(
                topic,
                ignored -> ConcurrentHashMap.newKeySet());
        topicEmitters.add(emitter);
        Runnable remove = () -> remove(topic, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("topic", topic)));
        } catch (IOException failure) {
            remove.run();
            emitter.completeWithError(failure);
        }
        return emitter;
    }

    /** 发布状态变化；单个断开的客户端不会影响数据库状态推进。 */
    public void publish(
            String topic,
            String resourceId,
            String convertStatus,
            String indexStatus) {
        publish(topic, resourceId, null, convertStatus, indexStatus);
    }

    public void publish(
            String topic,
            String resourceId,
            String originalStorageStatus,
            String convertStatus,
            String indexStatus) {
        Set<SseEmitter> topicEmitters = emitters.get(topic);
        if (topicEmitters == null || topicEmitters.isEmpty()) return;

        Map<String, Object> data = Map.of(
                "resourceId", resourceId,
                "originalStorageStatus", originalStorageStatus == null
                        ? "" : originalStorageStatus,
                "convertStatus", convertStatus == null ? "" : convertStatus,
                "indexStatus", indexStatus == null ? "" : indexStatus,
                "occurredAt", Instant.now().toString());
        for (SseEmitter emitter : topicEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("file-status")
                        .data(data));
            } catch (IOException failure) {
                remove(topic, emitter);
                log.debug("移除已断开的 SSE 文件状态订阅: topic={}", topic);
            }
        }
    }

    /**
     * Servlet SSE 没有内建心跳；定期发送注释事件，避免代理把空闲连接误判为断开。
     */
    @Scheduled(fixedDelay = 15_000)
    public void heartbeat() {
        for (Map.Entry<String, Set<SseEmitter>> entry : emitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException failure) {
                    remove(entry.getKey(), emitter);
                    log.debug("移除心跳失败的 SSE 文件状态订阅: topic={}", entry.getKey());
                }
            }
        }
    }

    private void remove(String topic, SseEmitter emitter) {
        Set<SseEmitter> topicEmitters = emitters.get(topic);
        if (topicEmitters == null) return;
        topicEmitters.remove(emitter);
        if (topicEmitters.isEmpty()) emitters.remove(topic, topicEmitters);
    }
}
