CREATE TABLE bid_winning_supplier_detail (
    detail_id INT NOT NULL AUTO_INCREMENT COMMENT '明细行ID（主键）',
    notification_id INT NOT NULL COMMENT '定标通知ID（逻辑外键，关联 bid_winning_notification.notification_id）',
    
    -- 对应页面底部表格的列
    supplier_name VARCHAR(100) NOT NULL COMMENT '中标供应商名称',
    amount DECIMAL(15,2) NOT NULL COMMENT '中标价（元）',
    
    PRIMARY KEY (detail_id),
    KEY idx_notification_id (notification_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定标通知-中标结果明细表（支持页面底部表格）';