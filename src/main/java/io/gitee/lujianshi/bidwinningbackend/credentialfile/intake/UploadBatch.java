package io.gitee.lujianshi.bidwinningbackend.credentialfile.intake;

import java.util.List;

/**
 * Controller 将一次 multipart 请求转换后得到的内部上传任务。
 *
 * <p>该对象只描述请求生命周期内的输入，不表示其中的文件已经持久化。</p>
 *
 * @param bidRecordId 文件所属的投标记录 ID
 * @param items 本次请求中等待处理的文件条目
 * @since 1.0
 */
public record UploadBatch(
        Integer bidRecordId,
        List<PendingUploadItem> items) {

    /** 紧凑构造器
     * <p> 校验批次输入并创建文件列表的不可变副本。</p> */
    public UploadBatch {
        if (bidRecordId == null || bidRecordId <= 0) {
            throw new IllegalArgumentException("投标记录 ID 不正确");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (items.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        items = List.copyOf(items);
    }
}
