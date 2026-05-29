package io.gitee.lujianshi.bidwinningbackend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件路径搜索服务
 *
 * @Service = 标注为 Spring 容器中的 Bean
 *
 * 职责：
 * - 动态扫描 classpath:/static/ 目录下的资源文件
 * - 根据关键字模糊匹配文件路径
 *
 * 注意：ResourcePatternResolver 是 Spring 接口，实现类是 PathMatchingResourcePatternResolver
 * 接口仅有一个实现类时，@Autowired 接口 Spring 会自动注入该实现类 Bean
 */
@Service
public class FilePathService {

    @Autowired
    private ResourcePatternResolver resourcePatternResolver;

    /**
     * 根据关键字搜索文件路径
     *
     * Stream 操作链说明：
     * 1. Arrays.stream(resources)：Arrays 是 java.util 工具类，stream(T[]) 将数组转换为 java.util.stream.Stream 实例
     * 2. .map(this::extractPath)：一对一转换，将 Resource 对象转换为路径字符串
     * 3. .filter(path -> path != null)：过滤掉 extractPath 返回 null 的元素
     * 4. .filter(path -> path.contains(keyword))：过滤出路径中包含关键字的文件
     * 5. .map(path -> "/" + path)：给路径拼接前导斜杠，格式化为 /static/images/logo.png
     * 6. .collect(Collectors.toList())：将流收集为 List，触发惰性求值
     *
     * @param keyword 搜索关键字（contains 模糊匹配）
     * @return 匹配的文件路径列表
     * @throws IOException 资源扫描可能抛出 IO 异常
     */
    public List<String> searchByKeyword(String keyword) throws IOException {
        Resource[] resources = resourcePatternResolver.getResources("classpath:/static/**/*");
        return Arrays.stream(resources)
                .map(this::extractPath)
                .filter(path -> path != null)
                .filter(path -> path.contains(keyword))
                .map(path -> "/" + path)
                .collect(Collectors.toList());
    }

    /**
     * 从 Resource 对象提取相对路径字符串
     *
     * Resource：Spring 统一资源抽象接口（org.springframework.core.io.Resource）
     * - 存储路径/URL/定位信息，不存储文件本身
     * - 具体实现类：ClassPathResource、UrlResource、FileSystemResource 等
     * - Java 多态机制：调用 Resource 接口方法，具体执行哪个实现类逻辑由 JVM 决定
     *
     * resource.getURL().getPath()：
     * - Resource.getURL() 返回 java.net.URL（可能抛 IOException）
     * - URL.getPath() 获取完整系统路径，例如：/Users/.../target/classes/static/images/logo.png
     *
     * 提取逻辑：用 indexOf("static") 找到 static 起始位置，截取相对路径
     * - 避免路径前缀差异（不同系统、不同构建工具路径不同）
     *
     * @param resource Spring 封装的资源文件对象
     * @return 相对于 static 目录的路径字符串，如 static/images/logo.png
     */
    private String extractPath(Resource resource) {
        try {
            String url = resource.getURL().getPath();
            int idx = url.indexOf("static");
            return idx > -1 ? url.substring(idx) : url;
        } catch (IOException e) {
            return null;
        }
    }
}