package io.gitee.lujianshi.bidwinningbackend.storage.vector.model;

/** 已从 MySQL 展开全部关联字段、可提交给 vector-service 的结构化投标记录。 */
public record PreparedBidRecordDocument(
        Integer bidRecordId,
        String content,
        Integer invitationId,
        String bidderName,
        Integer bidNo,
        Long bidderAccountId,
        String projectNo,
        String purchaseTaskNo,
        String bidAt,
        String quotePrice) {
}
