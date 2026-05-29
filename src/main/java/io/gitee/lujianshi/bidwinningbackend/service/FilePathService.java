package io.gitee.lujianshi.bidwinningbackend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FilePathService {

    @Autowired
    private ResourcePatternResolver resourcePatternResolver; // 核心功能是：用 Ant 风格的通配符（如 **/*）扫描 classpath 下的资源文件

    // 对外提供的核心方法：根据关键字，在 classpath:/static/ 目录下搜索所有匹配的文件路径
    public List<String> searchByKeyword(String keyword) throws IOException {
        Resource[] resources = resourcePatternResolver.getResources("classpath:/static/**/*");
        return Arrays.stream(resources)     // 把 Resource[] 数组 转换成 Java Stream 流
                .map(this::extractPath)     //  把 Resource 对象转换成文件路径字符串
                .filter(path -> path.contains(keyword)) //过滤出包含关键字的路径
                .map(path -> "/" + path)    // 给路径拼接上 /
                .collect(Collectors.toList());    // 把流收集成 List 并返回
    }

    // 接收一个 Resource 对象（Spring 封装的资源文件）
    // 返回该资源文件相对于 static 目录的路径字符串
    private String extractPath(Resource resource) {
        try {
            String url = resource.getURL().getPath();
            int idx = url.indexOf("static");
            return idx > -1 ? url.substring(idx) : url;
        } catch (IOException e){
            return null;
        }
    }
}
