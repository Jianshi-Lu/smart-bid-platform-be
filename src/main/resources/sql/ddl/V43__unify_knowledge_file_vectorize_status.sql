-- V43: 知识文件向量化状态值统一为 VECTORIZING / VECTORIZED
-- 旧值 INDEXING / INDEXED 仅存在于存量测试数据；同步更新，
-- 避免 markVectorizing 的条件更新（IN ('PENDING','FAILED','VECTORIZED')）无法抢占旧行。

UPDATE knowledge_file
SET index_status = 'VECTORIZING'
WHERE index_status = 'INDEXING';

UPDATE knowledge_file
SET index_status = 'VECTORIZED'
WHERE index_status = 'INDEXED';
