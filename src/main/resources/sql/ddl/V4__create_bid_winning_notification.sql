CREATE TABLE bid_winning_notification (
    notification_id INT NOT NULL AUTO_INCREMENT COMMENT '定标通知ID（主键）',
    handler_id INT NOT NULL COMMENT '经办人ID（外键，关联招标人员表）',
    
    -- 页面上部的项目信息
    project_no VARCHAR(50) NOT NULL COMMENT '项目编号',
    purchase_task_no VARCHAR(50) NOT NULL COMMENT '采购任务编号',
		platform_category VARCHAR(50) COMMENT '平台采购类别',
    
    -- 页面中部的招标规则信息
    bidding_process VARCHAR(50) COMMENT '招标流程',
		bidding_method VARCHAR(50) COMMENT '招标方式',
    purchase_method VARCHAR(50) COMMENT '采购方式',
    is_fixed_price TINYINT(1) COMMENT '是否固定总价（1是 0否）',
    target_cost DECIMAL(15,2) COMMENT '目标成本',
    document_fee DECIMAL(10,2) COMMENT '标书费',
    deposit DECIMAL(15,2) COMMENT '保证金',
    credit_report_required TINYINT(1) COMMENT '是否需要征信报告（1是 0否）',
    
    -- 页面中部的时间线信息
    registration_deadline DATETIME COMMENT '报名截止时间',
    tech_bid_deadline DATETIME COMMENT '技术回标截止时间',
    business_bid_deadline DATETIME COMMENT '商务回标截止时间',
    
    -- 页面底部的单行辅助信息
    bidding_status VARCHAR(20) DEFAULT '已完成' COMMENT '状态',
    notes TEXT COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    PRIMARY KEY (notification_id),
    KEY idx_staff_id (handler_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定标通知主表';