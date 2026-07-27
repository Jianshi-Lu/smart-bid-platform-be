# Credential file 完整链路 Postman 测试

Collection：`credential-file-flow.postman_collection.json`

## 测试前提

1. Java 服务已启动，例如 `http://127.0.0.1:8080`。
2. Python `to-markdown` 服务已启动，例如 `http://127.0.0.1:8081`。
3. Java 配置使用 MinIO：

   ```bash
   CREDENTIAL_FILE_STORAGE_TYPE=MINIO
   ```

4. Java、Python 使用同一个 MinIO endpoint、bucket 和凭证。
5. `bidRecordId` 已经存在。
6. 修改 collection 变量：

   - `bidRecordId`：已存在的投标记录 ID；
   - `fileAPath`：第一个本地测试文件绝对路径；
   - `fileBPath`：第二个本地测试文件绝对路径。

如果只测试一个文件，可以删除上传请求中第二个 `files` form-data 项。

## 执行顺序

按顺序执行：

1. `00 Prerequisite / 确认 bid_record 存在`
2. `01 Chain - upload original / 上传多个 original 文件`
3. `02 Chain - convert original to markdown / 按 bidRecordId 转换全部文件批次`
4. `02 Chain - ... / 验证 bid_record.credentials 已回写 converted object key`
5. `03 Chain - aggregate and analyze / 汇总 Markdown 并调用大模型`
6. `03 Chain - ... / 验证 risk_analysis 已落库`

## 验证内容

上传链会验证：

- Java 返回 `uploadBatchId`；
- 每个文件状态为 `UPLOADED`；
- `originalFilePath` 位于 `bid-records/{bidRecordId}/batches/{uploadBatchId}/original/`；
- 文件大小和 SHA-256 已返回。

转换链会验证：

- Java 找到该 `bidRecordId` 下的文件批次；
- 所有批次状态为 `CONVERTED`；
- 每个批次的 `convertedFileCount` 等于 `fileCount`；
- `bid_record.credentials` 已回写 `converted/*.md` object key。

风险分析链会验证：

- 所有批次和文件均已转换完成后才能进入分析；
- `bid-records/{bidRecordId}/merged/credentials.md` 作为汇总文件目标路径；
- 大模型返回结构化风险结果；
- 返回的 `analysisId` 已经可以在 `risk_analysis` 历史接口中查询到。

原始文件能否被 Python 读取、转换并写入 MinIO，是通过第二条链路间接验证的；汇总文件本身没有单独的下载接口，
其写入是否成功由风险分析接口能否读取并完成模型分析来验证。也可以根据 collection 变量
`mergedMarkdownPath` 到 MinIO 中手工检查对象。
