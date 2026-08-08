package io.gitee.lujianshi.bidwinningbackend.enums;

/**
 * 操作历史 operation 枚举：收敛为增删改三类。
 *
 * <p>具体业务阶段（如文件上传/转换/向量化、公告状态变更）不再单独占用
 * operation 值，通过 before/after 快照差异即可推导；历史表只记录写操作。</p>
 */
public enum OperationType {
    /** 增：新增记录（文件上传、公告/经办人/投标记录创建）。 */
    CREATE,

    /** 改：更新记录（含状态变更、文件转换/向量化/重试导致的状态推进）。 */
    UPDATE,

    /** 删：删除记录。 */
    DELETE,
}
