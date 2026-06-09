CREATE TABLE handler_info (
    handler_id INT NOT NULL AUTO_INCREMENT COMMENT '经办人ID（主键）',
    handler_name VARCHAR(50) NOT NULL COMMENT '经办人姓名',
    PRIMARY KEY (handler_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经办人信息表';