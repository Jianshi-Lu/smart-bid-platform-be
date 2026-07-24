package io.gitee.lujianshi.bidwinningbackend.credentialfile.controller;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.FileConvertResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileConvertService;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 资信文件 Markdown 转换 Controller。 */
@RestController
public class FileConvertController {

    @Resource
    private FileConvertService conversionService;

    /** 转换指定投标记录下的全部资信文件批次。 */
    @PostMapping("/credential-files/bid-records/{bidRecordId}/convert")
    public Result<FileConvertResponse> convert(
            @PathVariable Integer bidRecordId) {
        FileConvertResponse response =
                conversionService.convertAllBatches(bidRecordId);
        return Result.success(response, response.message());
    }
}
