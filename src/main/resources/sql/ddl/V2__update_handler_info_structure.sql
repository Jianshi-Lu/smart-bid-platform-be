ALTER TABLE handler_info 
	ADD COLUMN company_name VARCHAR (100) NOT NULL COMMENT '所属采购公司' AFTER handler_name,
	ADD COLUMN department_name VARCHAR (100) NOT NULL COMMENT '所属采购部门' AFTER company_name;

ALTER table handler_info
COMMENT = '招标负责人（甲方）信息表'