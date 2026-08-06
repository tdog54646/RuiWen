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
 *     k: 20              # 当前语料评测值；值越大各路排名的权重越均衡
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

    /** RAG 索引生命周期配置。 */
    private Index index = new Index();

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

    /** 离线评测配置。 */
    private Evaluation evaluation = new Evaluation();

    @Data
    public static class Index {
        /** 生产读写使用的稳定别名。 */
        private String alias = "ruiwen-ai-read";
        /** 首次迁移时兼容的旧物理索引。 */
        private String legacyName = "ruiwen-ai-index";
        /** 当前目标物理索引版本。 */
        private int version = 2;
        /** dense_vector 维度，必须与 Embedding 模型一致。 */
        private int dimensions = 1536;
        /** 中文写入分词器。 */
        private String analyzer = "ik_max_word";
        /** 中文查询分词器。 */
        private String searchAnalyzer = "ik_smart";

        public String physicalName() {
            return legacyName + "-v" + version;
        }
    }

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

        // ─────────────────────────────────────────────────────────────────────
        // Markdown 结构感知切分配置（mode B / mode A）
        // ─────────────────────────────────────────────────────────────────────
        /**
         * 是否启用 Markdown 结构感知切分。
         * false = 退化为原有段落 + TokenTextSplitter 逻辑。
         */
        private boolean markdownAware = true;
        /**
         * 切分模式：rule（纯规则，默认）/ llm（LLM 增强）。
         */
        private ChunkMode mode = ChunkMode.RULE;
        /**
         * 是否启用 Header Anchoring（标题与紧随内容合并）。
         * true = 标题与紧随段落/代码块/列表合并，不拆散。
         */
        private boolean headerAnchoring = true;
        /**
         * 获取 overlap 占 chunk size 的百分比。
         */
        public int getOverlapPercent() {
            if (size <= 0) return 0;
            return (int) (overlap * 100.0 / size);
        }
    }

    /** 切分模式枚举 */
    public enum ChunkMode {
        /** 纯规则模式：MarkdownBlockParser + SectionMerger（推荐，零成本） */
        RULE,
        /** LLM 增强模式：调用 DeepSeek 完成结构提取和合并 */
        LLM
    }

    @Data
    public static class Retrieval {
        /** 每路检索各自召回的 Top 数量。推荐值：20~50。太小漏召，太大增加 RRF 开销。 */
        private int topK = 20;
        /** KNN HNSW 候选池，必须不小于 topK。 */
        private int knnCandidates = 50;
        /** RRF 最低得分；这是约 0.01~0.03 的排名分数，不是 0~1 相似度。 */
        private double minScore = 0.0;
        /** BM25 标题字段权重。 */
        private double titleBoost = 1.0;
        /** BM25 正文短语匹配权重；0 表示不添加短语子句。 */
        private double phraseBoost = 0.0;
        /** BM25 短语查询允许的词序距离。 */
        private int phraseSlop = 0;
        /** multi_match 至少需要命中的分词数量或比例，例如 1、50%。 */
        private String minimumShouldMatch = "1";
        /** multi_match 字段融合方式：best_fields、most_fields 或 cross_fields。 */
        private String queryType = "best_fields";
        /** multi_match 模糊匹配参数；NONE 表示关闭，AUTO 表示自动编辑距离。 */
        private String fuzziness = "NONE";
        /** 最终上下文是否抑制近重复内容。 */
        private boolean diversityEnabled = true;
        /** 归一化字符 5-gram Jaccard 达到该值视为近重复。 */
        private double nearDuplicateThreshold = 0.45;
    }

    @Data
    public static class Rrf {
        /**
         * RRF 算法的平滑常数 K。
         * 公式：RRF_Score(d) = Σ 1.0 / (K + rank_i(d))
         * <ul>
         *   <li>K 越大，不同排名之间的得分差异越小，各路影响力越均衡。</li>
         *   <li>K=0 时退化为严格排名比较（只看 rank 不看分数）。</li>
         *   <li>通用起点常取 60；当前语料实测 20 的召回相同且冗余更低。</li>
         * </ul>
         */
        private int k = 20;
    }

    @Data
    public static class Rerank {
        /** 是否启用真实 Reranker；失败时按 failOpen 配置降级到 RRF。 */
        private boolean enabled = false;
        /** RRF 融合后送入 Reranker 做精排的 Top 数量。精排开销较大，通常取 10~20。 */
        private int topK = 20;
        /** DashScope 文本排序接口。 */
        private String apiUrl = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
        /** DashScope Reranker 模型。 */
        private String model = "gte-rerank-v2";
        /** Reranker API Key。 */
        private String apiKey;
        /** 经评测标定后才应设置；0 表示不过滤。 */
        private double minScore = 0.0;
        /** 外部精排失败时是否保留 RRF 结果。 */
        private boolean failOpen = true;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 10000;
    }

    @Data
    public static class Prompt {
        /** 最终送入 LLM 的最大 Chunk 数量。通常取 3~10。 */
        private int contextLimit = 5;
        /** 检索为空时的兜底回答。 */
        private String emptyAnswer = "未找到相关信息，请尝试调整问题的表述，或换一种问法。";
    }

    @Data
    public static class Evaluation {
        /** 离线评测并发度；线上请求不使用该配置。 */
        private int parallelism = 1;
        /** 评测集默认只接受人工审核通过的数据。 */
        private boolean reviewedOnly = true;
        /** 候选召回阶段的指标 K。 */
        private int candidateK = 20;
        /** 最终上下文阶段的指标 K。 */
        private int finalK = 5;
        /** 网格评测候选的每路召回数量。 */
        private java.util.List<Integer> retrievalTopKGrid = java.util.List.of(20, 40);
        /** 网格评测的 KNN 候选池。 */
        private java.util.List<Integer> knnCandidatesGrid = java.util.List.of(50, 100);
        /** 网格评测的最终上下文数量。 */
        private java.util.List<Integer> finalTopKGrid = java.util.List.of(3, 5, 8);
        /** 网格评测的 RRF 平滑常数。 */
        private java.util.List<Integer> rrfKGrid = java.util.List.of(20);
        /** 网格评测的 BM25 标题权重。 */
        private java.util.List<Double> titleBoostGrid = java.util.List.of(1.0);
        /** 网格评测的 BM25 正文短语权重。 */
        private java.util.List<Double> phraseBoostGrid = java.util.List.of(0.0);
        /** 最终上下文近重复阈值网格；只派生选择结果，不重复调用 ES/Embedding。 */
        private java.util.List<Double> nearDuplicateThresholdGrid =
                java.util.List.of(0.35, 0.45, 0.55, 0.65);
        /** SILVER 标注使用的独立 OpenAI-compatible Chat Completions 地址。 */
        private String judgeApiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
        /** SILVER 标注模型，不参与线上问答。 */
        private String judgeModel = "qwen-plus";
        /** SILVER 标注模型的独立 API Key。 */
        private String judgeApiKey;
        /** 单个问题的裁判超时，避免批处理因上游抖动永久挂起。 */
        private int judgeTimeoutSeconds = 45;
        /** 裁判最大输出长度；跨文章标签和参考答案需要高于普通评分响应。 */
        private int judgeMaxTokens = 4096;
        /** 独立裁判不可用时，是否降级到现有 DeepSeek ChatClient。 */
        private boolean judgeFallbackEnabled = true;
    }
}
