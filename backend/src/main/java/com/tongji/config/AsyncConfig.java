package com.tongji.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 异步支持配置。
 * <p>SSE 等返回 {@code Flux}/reactive 类型的接口在 servlet 栈下走 MVC 异步；
 * 不显式配 executor 时回退到默认 {@code SimpleAsyncTaskExecutor}（每请求新建线程、无界，
 * 会打 WARN 并在生产高并发下有风险）。这里把异步 executor 显式设为虚拟线程 executor：
 * 既消除该警告，又让 SSE（长连接、阻塞 LLM 调用）跑在轻量虚拟线程上。
 */
@Configuration
public class AsyncConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(new VirtualThreadTaskExecutor("mvc-async-"));
    }
}
