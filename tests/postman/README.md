# Credential file Postman 说明

`credential-file-flow.postman_collection.json` 保留的是批次化上传设计时期的历史 collection：
它仍引用 `uploadBatchId`、`credential_file_batch` 和旧对象路径，不能作为当前接口的联调或回归依据。

当前资信材料契约以 [docs/api.md](../../docs/api.md#5-资信文件上传credentialfile) 和
[docs/business-process.md](../../docs/business-process.md#3-资信材料上传转换与下载) 为准：

1. 使用投标方 Basic 凭据创建或选择属于该账号的 `bidRecordId`。
2. `POST /bidder/bid-records/{bidRecordId}/credential-files`，以重复 `files` 和匹配的 `username` 提交多个文件。
3. 立即检查上传响应的每个文件；它可能仍处于 `UPLOADED`，不应把它当作转换完成。
4. 通过 `GET /bidder/bid-records/{bidRecordId}/credential-files` 和同路径 `/events` 观察 `originalStorageStatus`、`convertStatus`、错误阶段和最终路径。
5. 原文件下载要求 original 路径与 SHA-256 已回填；`.md` 下载还要求转换完成。

如需维护 Postman 自动化，应新建基于上述端点、Basic 鉴权和单文件状态机的 collection；不要在旧 collection 上继续叠加变量或断言。
