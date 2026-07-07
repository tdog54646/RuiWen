package com.tongji.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.tongji.storage.config.OssProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 启动依赖自检报告：应用就绪后打印各依赖配置（脱敏）+ 连通性自检结果。
 * <p>设计要点：
 * <ul>
 *   <li>失败只报告，不阻断启动 —— 所有自检 try/catch，异常只计入报告 ✗。</li>
 *   <li>ES 用 GET（info / indices.get）避开 ES 9.2.1 对 HEAD 请求返回 chunked 不结束的 bug。</li>
 *   <li>每项自检用 CompletableFuture 统一超时包裹，避免慢连接拖慢启动。</li>
 *   <li>密码 / 密钥统一脱敏，不在日志泄露明文。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupEnvironmentReporter implements ApplicationRunner {

    private static final long CHECK_TIMEOUT_MS = 5000L;
    private static final String AI_INDEX = "ruiwen-ai-index";
    private static final String CONTENT_INDEX = "ruiwen_content_index";

    private final Environment env;
    private final JdbcTemplate jdbcTemplate;
    private final RedissonClient redissonClient;
    private final RedisProperties redisProperties;
    private final ElasticsearchClient elasticsearchClient;
    private final EsProperties esProperties;
    private final OssProperties ossProperties;

    @Override
    public void run(ApplicationArguments args) {
        List<CheckResult> results = new ArrayList<>();

        results.add(check("MySQL", () -> {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "SELECT 1 OK";
        }));

        results.add(check("Redis", () -> {
            // 复用项目已验证的 Redisson API；true/false 均代表连通成功
            redissonClient.getBucket("startup:probe").isExists();
            return "PING OK";
        }));

        results.add(check("Elasticsearch", () -> {
            String ver = elasticsearchClient.info().version().number();
            return "info OK, version=" + ver;
        }));

        results.add(check("ES索引 " + AI_INDEX, () -> indexState(AI_INDEX)));
        results.add(check("ES索引 " + CONTENT_INDEX, () -> indexState(CONTENT_INDEX)));

        final String kafkaBootstrap = env.getProperty("spring.kafka.bootstrap-servers", "");
        results.add(check("Kafka", () -> tcpReachable(kafkaBootstrap)));

        final boolean canalEnabled = Boolean.parseBoolean(env.getProperty("canal.enabled", "true"));
        final String canalHost = env.getProperty("canal.host", "");
        final int canalPort = Integer.parseInt(env.getProperty("canal.port", "11111"));
        results.add(check("Canal", () -> {
            if (!canalEnabled) {
                return "canal.enabled=false，跳过";
            }
            // 仅测 TCP 端口可达，不真连 Canal 协议，避免干扰 CanalKafkaBridge 异步桥
            return tcpReachable(canalHost + ":" + canalPort);
        }));

        results.add(check("OSS", () -> {
            OSS client = new OSSClientBuilder().build(ossProperties.getEndpoint(),
                    ossProperties.getAccessKeyId(), ossProperties.getAccessKeySecret());
            try {
                boolean exists = client.doesBucketExist(ossProperties.getBucket());
                return exists ? "bucket " + ossProperties.getBucket() + " 存在" : "bucket 不存在";
            } finally {
                client.shutdown();
            }
        }));

        log.info(buildReport(results));
    }

    /** 用 GET 判断索引是否存在，避开 ES 9.2.1 的 HEAD bug。 */
    private String indexState(String idx) throws Exception {
        try {
            elasticsearchClient.indices().get(g -> g.index(idx));
            return "存在";
        } catch (Exception e) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            if (m.contains("404") || m.contains("not_found") || m.contains("index_not_found")) {
                return "不存在";
            }
            throw e;
        }
    }

    /** 测 CSV 形如 host:port[,host:port...] 的第一个地址 TCP 可达性。 */
    private String tcpReachable(String hostPortCsv) throws Exception {
        String first = hostPortCsv.split(",")[0].trim();
        String[] hp = first.split(":");
        if (hp.length != 2) {
            throw new IllegalStateException("地址格式异常: " + hostPortCsv);
        }
        String host = hp[0];
        int port = Integer.parseInt(hp[1]);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) CHECK_TIMEOUT_MS);
        }
        return "TCP " + host + ":" + port + " 可达";
    }

    private String buildReport(List<CheckResult> results) {
        long ok = results.stream().filter(CheckResult::ok).count();
        StringBuilder sb = new StringBuilder();
        sb.append("\n+==================== 启动依赖自检 ====================+\n");

        sb.append(String.format("| %-12s | port=%s, profile=%s%n", "应用",
                env.getProperty("server.port", "8080"),
                env.getProperty("spring.profiles.active", "default")));

        sb.append(String.format("| %-12s | url=%s%n", "MySQL",
                abbrev(env.getProperty("spring.datasource.url", ""), 60)));
        sb.append(String.format("| %-12s | user=%s, password=%s%n", "",
                env.getProperty("spring.datasource.username", ""),
                mask(env.getProperty("spring.datasource.password", ""))));
        appendCheck(sb, results, "MySQL");

        sb.append(String.format("| %-12s | host=%s:%s, db=%s, password=%s%n", "Redis",
                redisProperties.getHost(), redisProperties.getPort(),
                redisProperties.getDatabase(),
                mask(redisProperties.getPassword())));
        appendCheck(sb, results, "Redis");

        sb.append(String.format("| %-12s | uri=%s%n", "Elasticsearch",
                abbrev(esProperties.getHost() == null ? "" : esProperties.getHost(), 60)));
        appendCheck(sb, results, "Elasticsearch");
        appendCheck(sb, results, "ES索引 " + AI_INDEX);
        appendCheck(sb, results, "ES索引 " + CONTENT_INDEX);

        sb.append(String.format("| %-12s | bootstrap-servers=%s%n", "Kafka",
                env.getProperty("spring.kafka.bootstrap-servers", "")));
        appendCheck(sb, results, "Kafka");

        sb.append(String.format("| %-12s | enabled=%s, host=%s:%s, dest=%s, password=%s%n", "Canal",
                env.getProperty("canal.enabled", "true"),
                env.getProperty("canal.host", ""), env.getProperty("canal.port", "11111"),
                env.getProperty("canal.destination", ""),
                mask(env.getProperty("canal.password", ""))));
        appendCheck(sb, results, "Canal");

        sb.append(String.format("| %-12s | endpoint=%s, bucket=%s, domain=%s%n", "OSS",
                ossProperties.getEndpoint(), ossProperties.getBucket(),
                ossProperties.getPublicDomain()));
        sb.append(String.format("| %-12s | accessKeyId=%s, secret=%s%n", "",
                tail4(ossProperties.getAccessKeyId()),
                mask(ossProperties.getAccessKeySecret())));
        appendCheck(sb, results, "OSS");

        sb.append(String.format("| %-12s | base-url=%s, model=%s, key=%s%n", "AI(DeepSeek)",
                env.getProperty("spring.ai.deepseek.base-url", ""),
                env.getProperty("spring.ai.deepseek.chat.options.model", ""),
                mask(env.getProperty("spring.ai.deepseek.api-key", ""))));
        sb.append(String.format("| %-12s | base-url=%s, model=%s, dim=%s, key=%s%n", "AI(Embed)",
                env.getProperty("spring.ai.openai.base-url", ""),
                env.getProperty("spring.ai.openai.embedding.options.model", ""),
                env.getProperty("spring.ai.openai.embedding.options.dimensions", ""),
                mask(env.getProperty("spring.ai.openai.api-key", ""))));

        sb.append(String.format("+==================== 自检完成：%d/%d 项正常 ====================+",
                ok, results.size()));
        return sb.toString();
    }

    private void appendCheck(StringBuilder sb, List<CheckResult> results, String name) {
        results.stream().filter(r -> r.name().equals(name)).findFirst().ifPresent(r ->
                sb.append(String.format("| %-12s |   %s %s (%dms) — %s%n", "",
                        r.ok() ? "[ OK ]" : "[FAIL]",
                        r.ok() ? "连通" : "失败",
                        r.durationMs(), r.detail())));
    }

    /** 带超时执行自检；失败/超时均返回 ✗，绝不向上抛。 */
    private CheckResult check(String name, ThrowingSupplier supplier) {
        long start = System.currentTimeMillis();
        try {
            String detail = CompletableFuture.supplyAsync(() -> {
                try {
                    return supplier.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).get(CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return new CheckResult(name, true, System.currentTimeMillis() - start, detail);
        } catch (TimeoutException e) {
            return new CheckResult(name, false, System.currentTimeMillis() - start,
                    "超时(>" + CHECK_TIMEOUT_MS + "ms)");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause.getCause() != null) {
                cause = cause.getCause();
            }
            return new CheckResult(name, false, System.currentTimeMillis() - start, firstReason(cause));
        }
    }

    private String firstReason(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur
                && (cur.getMessage() == null || cur.getMessage().isBlank())) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage() == null ? "(无消息)" : cur.getMessage();
        msg = msg.split("\\R", 2)[0];
        return cur.getClass().getSimpleName() + ": " + abbrev(msg, 120);
    }

    private String mask(String s) {
        return (s == null || s.isBlank()) ? "未配置" : "已配置(长度" + s.length() + ")";
    }

    private String tail4(String s) {
        if (s == null || s.isBlank()) {
            return "未配置";
        }
        return s.length() <= 4 ? "****" : "..." + s.substring(s.length() - 4);
    }

    private String abbrev(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private record CheckResult(String name, boolean ok, long durationMs, String detail) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        String get() throws Exception;
    }
}
