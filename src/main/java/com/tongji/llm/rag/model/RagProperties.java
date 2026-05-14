package com.tongji.llm.rag.model;


import com.tongji.llm.rag.chunk.SemanticChunker;
import com.tongji.llm.rag.query.PromptTemplateService;
import com.tongji.llm.rag.search.HybridSearchService;
import com.tongji.llm.rag.search.RrfFusionService;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 链路各阶段的可配置参数。
 * <p>
 * 所有字段均可通过 application.yml 中的 {@code rag.} 前缀覆盖默认值。
 * 这些参数控制了分块策略、检索召回量、RRF 融合常数以及生成阶段的行为。
 *
 * <p>典型配置示例（application.yml）：
 * <pre>
 * rag:
 *   chunk:
 *     size: 500          # 每个 Chunk 的最大 token 数
 *     overlap: 50        # 相邻 Chunk 之间的重叠 token 数
 *     min-length: 50     # 丢弃短于该值的 Chunk（字符数）
 *     max-length: 2000   # 丢弃长于该值的 Chunk（字符数）
 *   retrieval:
 *     top-k: 20          # 每路检索的召回数量（向量路和 BM25 路各自召回这么多）
 *     min-score: 0.0     # Chunk 最低相似度阈值
 *   rrf:
 *     k: 60              # RRF 平滑常数，值越大各路排名的权重越均衡
 *   rerank:
 *     enabled: false     # 是否启用 Reranker 精排（默认关闭）
 *     top-k: 5           # RRF 融合后做精排的 Top 数量
 *   prompt:
 *     context-limit: 5   # 最终送入 LLM 的最大 Chunk 数量
 *     empty-answer: "未找到相关信息，请尝试调整问题表述。"
 * </pre>
 *
 * @see SemanticChunker
 * @see RrfFusionService
 * @see HybridSearchService
 * @see PromptTemplateService
 */
@Data
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** 分块配置 */
    private Chunk chunk = new Chunk();

    /** 检索配置 */
    private Retrieval retrieval = new Retrieval();

    /** RRF 融合配置 */
    private Rrf rrf = new Rrf();

    /** 精排配置 */
    private Rerank rerank = new Rerank();

    /** Prompt 配置 */
    private Prompt prompt = new Prompt();

    @Data
    public static class Chunk {
        /** 每个 Chunk 的目标 token 数量上限。 */
        private int size = 500;
        /** 相邻 Chunk 之间的重叠 token 数量。适当重叠可避免语义边界被切断。 */
        private int overlap = 50;
        /** Chunk 最小字符数。短于此值的 Chunk 会被丢弃。 */
        private int minLength = 0;
        /** Chunk 最大字符数。超过此值的超长 Chunk 会被截断。 */
        private int maxLength = 2000;
    }

    @Data
    public static class Retrieval {
        /** 每路检索各自召回的 Top 数量。推荐值：20~50。太小漏召，太大增加 RRF 开销。 */
        private int topK = 20;
        /** Chunk 最低相似度得分阈值。低于此值不纳入结果。 */
        private double minScore = 0.0;
    }

    @Data
    public static class Rrf {
        /**
         * RRF 算法的平滑常数 K。
         * 公式：RRF_Score(d) = Σ 1.0 / (K + rank_i(d))
         * <ul>
         *   <li>K 越大，不同排名之间的得分差异越小，各路影响力越均衡。</li>
         *   <li>K=0 时退化为严格排名比较（只看 rank 不看分数）。</li>
         *   <li>RAGFlow / LightRAG 等主流框架默认 K=60。</li>
         * </ul>
         */
        private int k = 60;
    }

    @Data
    public static class Rerank {
        /** 是否启用 Reranker 精排模型（如 BGE-Reranker）。false 时透传 RRF 结果。 */
        private boolean enabled = false;
        /** RRF 融合后送入 Reranker 做精排的 Top 数量。精排开销较大，通常取 10~20。 */
        private int topK = 20;
        /** Reranker API 的 base URL。 */
        private String apiUrl;
        /** Reranker API Key（可选）。 */
        private String apiKey;
    }

    @Data
    public static class Prompt {
        /** 最终送入 LLM 的最大 Chunk 数量。通常取 3~10。 */
        private int contextLimit = 5;
        /** 检索为空时的兜底回答。 */
        private String emptyAnswer = "未找到相关信息，请尝试调整问题的表述，或换一种问法。";
    }
}
