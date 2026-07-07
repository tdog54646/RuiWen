#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
批量生成测试数据：50 用户 + 300 文章（知文）。

- 连接信息从项目根目录 .env 读取（SPRING_DATASOURCE_URL / DB_USERNAME / DB_PASSWORD）
- 用户 id 自增；文章 id 用与后端等价的雪花算法生成（41 位时间 + 5 DC + 5 worker + 12 序列）
- 密码使用 bcrypt(strength=12)，所有用户统一密码 Test1234!（可登录验证）
- 文章正文写成真实 Markdown，落到 frontend/public/static/posts/seed-<id>.md
  content_url 存 /static/posts/seed-<id>.md —— 前端详情页同源 fetch 可直接读取
- 幂等：重跑前先清理上一轮 seed 数据（email LIKE %@seed.ruiwen 的用户、
  content_object_key LIKE 'seed/posts/%' 的文章）与旧的 seed-*.md 文件

用法：
    python3 scripts/seed_data.py            # 默认 50 用户 / 300 文章
    python3 scripts/seed_data.py 100 600    # 100 用户 / 600 文章
"""
import os
import re
import sys
import json
import shutil
import hashlib
import random
import time
import datetime as dt
from pathlib import Path

try:
    import pymysql
except ImportError:
    sys.exit("缺少依赖 pymysql，请先执行: pip3 install pymysql bcrypt")

try:
    import bcrypt
except ImportError:
    sys.exit("缺少依赖 bcrypt，请先执行: pip3 install pymysql bcrypt")

# ----------------------------------------------------------------------------- 配置
PROJECT_ROOT = Path(__file__).resolve().parent.parent
ENV_PATH = PROJECT_ROOT / ".env"
MD_DIR = PROJECT_ROOT / "frontend" / "public" / "static" / "posts"
SEED_EMAIL_DOMAIN = "@seed.ruiwen"
SEED_OBJKEY_PREFIX = "seed/posts/"
SEED_MD_PREFIX = "seed-"
DEFAULT_PASSWORD = "Test1234!"           # 统一登录密码
BCRYPT_ROUNDS = 12                       # 与 application.yml auth.password.bcrypt-strength 一致
BASE_DATE = dt.datetime(2026, 7, 7, 12, 0, 0)  # 数据时间基准（今天）

rng = random.Random(20260707)            # 固定种子，内容可复现

# ----------------------------------------------------------------------------- .env 读取
def load_env(path: Path) -> dict:
    env = {}
    if not path.exists():
        sys.exit(f"找不到 .env 文件: {path}")
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip().strip('"').strip("'")
    return env


def parse_mysql_dsn(url: str):
    """从 jdbc:mysql://host:port/db?... 解析出 host/port/db。"""
    m = re.search(r"mysql://([^:/?#]+)(?::(\d+))?/([^?#/]+)", url)
    if not m:
        sys.exit(f"无法从 SPRING_DATASOURCE_URL 解析连接信息: {url}")
    return m.group(1), int(m.group(2) or 3306), m.group(3)


# ----------------------------------------------------------------------------- 雪花 ID（与后端 SnowflakeIdGenerator 等价）
class Snowflake:
    EPOCH = 1704067200000  # 2024-01-01 00:00:00 UTC，与后端一致
    _ts = -1
    _seq = 0
    worker_id = 1
    datacenter_id = 1

    @classmethod
    def next(cls) -> int:
        ts = int(time.time() * 1000)
        if ts == cls._ts:
            cls._seq = (cls._seq + 1) & 0xFFF
            if cls._seq == 0:
                while ts <= cls._ts:
                    ts = int(time.time() * 1000)
        else:
            cls._seq = 0
        cls._ts = ts
        return (((ts - cls.EPOCH) << 22)
                | (cls.datacenter_id << 17)
                | (cls.worker_id << 12)
                | cls._seq)


# ----------------------------------------------------------------------------- 内容池
SCHOOLS = ["同济大学", "清华大学", "北京大学", "复旦大学", "上海交通大学",
           "浙江大学", "南京大学", "武汉大学", "华中科技大学", "中山大学",
           "厦门大学", "东南大学", "北京航空航天大学", "哈尔滨工业大学"]

NICKNAME_ADJ = ["熬夜的", "快乐的", "迷茫的", "卷王", "佛系", "认真的",
                "摸鱼的", "暴躁的", "温柔的", "钢铁", "野生", "天才"]
NICKNAME_NOUN = ["码农", "考研人", "同济er", "刷题机器", "干饭人", "读书郎",
                 "前端仔", "后端汪", "算法选手", "图书馆常客", "夜猫子", "学生党"]

USER_TAGS_POOL = ["技术", "阅读", "考研", "篮球", "摄影", "游戏", "音乐",
                  "旅行", "美食", "健身", "二次元", "投资", "写作", "日语"]

BIO_POOL = [
    "热爱技术，偶尔写写笔记。", "记录生活与学习的点滴。",
    "在代码与咖啡之间寻找平衡。", "慢慢来，比较快。",
    "一个努力向上生长的普通人。", "保持好奇，保持热爱。",
    "今天也要加油鸭～", "分享学习日常，欢迎交流。",
    "业精于勤荒于嬉。", "种一棵树最好的时间是十年前，其次是现在。",
]

# 文章主题（主题 + 关联标签 + 段落素材）
TOPICS = [
    ("Java 并发编程", ["Java", "后端", "并发"], [
        "Java 的并发包 java.util.concurrent 提供了丰富的工具类。其中 ReentrantLock 相比 synchronized 提供了更灵活的锁控制，例如可中断、可超时、公平锁等特性。",
        "volatile 关键字保证了变量的可见性与有序性，但并不保证原子性。在 i++ 这类复合操作中，仍需要使用 AtomicXXX 或加锁。",
        "线程池的核心参数包括核心线程数、最大线程数、空闲存活时间、工作队列与拒绝策略。合理配置线程池是高并发系统的基本功。",
    ]),
    ("Spring Boot 实战", ["Spring", "后端", "框架"], [
        "Spring Boot 通过自动配置大幅简化了传统 Spring 应用的搭建成本。@EnableAutoConfiguration 是其核心注解之一。",
        "在 Spring Boot 中，自定义 Starter 只需要在 META-INF/spring.factories 中声明 AutoConfiguration 类即可被自动装配。",
        "事务注解 @Transactional 在方法内部调用时不会生效，这是由于 Spring AOP 基于代理实现的特性，需要通过注入或 AopContext 来规避。",
    ]),
    ("MySQL 索引优化", ["MySQL", "数据库", "后端"], [
        "B+ 树是 MySQL InnoDB 引擎使用的索引结构，它的非叶子节点只存储键值，所有数据都挂在叶子节点上，并且叶子节点通过双向链表连接，非常适合范围查询。",
        "覆盖索引指的是查询的字段全部被索引覆盖，从而避免回表，是常见的性能优化手段。可以用 EXPLAIN 查看 Extra 列是否出现 Using index。",
        "联合索引遵循最左前缀原则，索引 (a, b, c) 可以用于 a、(a,b)、(a,b,c) 的查询，但无法直接用于 (b,c) 的查询。",
    ]),
    ("Redis 高可用", ["Redis", "缓存", "后端"], [
        "Redis 的持久化机制分为 RDB 与 AOF 两种。RDB 是周期性的全量快照，AOF 则记录每一条写命令，前者恢复快、后者数据更完整。",
        "缓存雪崩是指大量缓存同时失效，请求打到数据库导致压力激增。常见解决方案是给过期时间加上随机扰动，或采用多级缓存。",
        "缓存穿透指的是查询一个数据库中根本不存在的数据，缓存永远无法命中。可以用布隆过滤器或缓存空值来缓解。",
    ]),
    ("React 学习笔记", ["React", "前端", "JavaScript"], [
        "React 的 Hooks 是 16.8 版本引入的特性，它允许我们在函数组件中使用状态与生命周期等能力。useState 与 useEffect 是最常用的两个 Hook。",
        "useEffect 的依赖数组决定了副作用何时重新执行。空数组表示只在挂载时执行一次，遗漏依赖则是新手最容易踩的坑之一。",
        "React 的渲染性能优化手段包括 React.memo、useMemo、useCallback，核心思想都是避免不必要的重渲染。",
    ]),
    ("考研数学复盘", ["考研", "数学", "学习"], [
        "高数中极限的求解方法有很多，洛必达法则适用于 0/0 或 ∞/∞ 型未定式，但要注意使用条件，有时先等价无穷小替换会更简洁。",
        "线性代数中矩阵的特征值与特征向量是核心考点。理解 AX=λX 的几何意义，有助于掌握对角化与相似矩阵。",
        "概率论的全概率公式与贝叶斯公式是重点，要理清先验概率与后验概率的关系，建议结合具体例题反复练习。",
    ]),
    ("算法刷题日记", ["算法", "LeetCode", "编程"], [
        "动态规划的核心在于状态定义与状态转移方程。以背包问题为例，定义 dp[i][j] 表示前 i 件物品在容量 j 下的最大价值是经典套路。",
        "二分查找不仅适用于有序数组，凡是具有单调性或二段性的场景都可以使用。关键在于如何设计 check 函数与边界更新。",
        "图的最短路径算法中，Dijkstra 适用于非负权图，Bellman-Ford 可以处理负权，而 Floyd 则是求多源最短路径的经典算法。",
    ]),
    ("校园生活随笔", ["校园", "生活", "随想"], [
        "图书馆的早晨总是格外安静，阳光透过落地窗洒在书桌上，翻开一本好书，整个人都平静了下来。",
        "食堂的糖醋排骨永远是心头好，虽然排队的人很多，但吃到的那一刻觉得一切都值得。",
        "期末季的自习室里坐满了人，键盘声、翻书声此起彼伏，每个人都在为各自的目标默默努力着。",
    ]),
    ("读书分享", ["阅读", "思考", "生活"], [
        "最近读完了《被讨厌的勇气》，书中阿德勒心理学的目的论让我印象深刻：我们不是被过去决定，而是被自己赋予过去的意义所决定。",
        "《深入理解 Java 虚拟机》是 JVM 领域的经典之作，从类加载机制到垃圾回收，每一章都值得反复咀嚼。",
        "《代码整洁之道》告诉我们，代码是写给人看的，只是顺便能在机器上运行。良好的命名与小函数是可维护性的基石。",
    ]),
    ("Python 数据处理", ["Python", "数据", "编程"], [
        "Pandas 是 Python 数据处理的利器。DataFrame 的 groupby + agg 组合可以高效完成分组聚合，比循环快上几个数量级。",
        "列表推导式是 Python 中非常 Pythonic 的写法，[x*x for x in range(10) if x % 2 == 0] 既简洁又高效。",
        "虚拟环境管理推荐使用 venv 或 conda，隔离不同项目的依赖版本，避免全局环境污染。",
    ]),
]

# 标题模板
TITLE_TPL = [
    "{topic}：{tail}", "{topic}之 {tail}", "关于 {topic} 的 {tail}",
    "{tail} —— {topic} 小记", "{topic}{tail}全记录", "聊聊 {topic}：{tail}",
]
TITLE_TAIL = ["入门到精通", "踩坑实录", "学习笔记", "实战总结", "核心概念梳理",
              "常见误区", "面试要点", "我的理解", "深度复盘", "从零开始",
              "那些事", "进阶指南", "最佳实践", "原理浅析", "踩过的那些坑"]

MD_INTRO = ["最近在系统地复习相关知识，顺手记录一下。", "这篇整理一下我最近的学习成果。",
            "正好有空，把之前零散的笔记汇总一下。", "下面是一些个人总结，欢迎指正。",
            "趁热打铁，记录下这次实践的过程与思考。"]
MD_OUTRO = ["以上就是今天的全部内容，如有错误欢迎指正～",
            "如果对你有帮助，求个点赞收藏，Thanks♪(･ω･)ﾉ",
            "后续会继续更新相关内容，敬请期待。",
            "学习是个长期的过程，一起加油吧！",
            "有疑问欢迎在评论区交流。"]


def gen_title(topic: str) -> str:
    return rng.choice(TITLE_TPL).format(topic=topic, tail=rng.choice(TITLE_TAIL))


def gen_md(post_id: int, topic: str, paragraphs: list, tags: list) -> tuple:
    """生成 Markdown 正文，返回 (md_text, description)。"""
    n_para = rng.randint(2, len(paragraphs))
    chosen = rng.sample(paragraphs, n_para)
    lines = [f"# {gen_title(topic)}", ""]
    lines.append(f"> {rng.choice(MD_INTRO)}")
    lines.append("")
    # 标签
    lines.append("标签：" + "、".join(f"`{t}`" for t in tags))
    lines.append("")
    # 正文小节
    for i, p in enumerate(chosen, 1):
        lines.append(f"## {rng.choice(['一', '二', '三', '四', '五'][i-1:i])}、{rng.choice(['核心要点', '实践细节', '深入理解', '常见问题', '小结'])}")
        lines.append("")
        lines.append(p)
        lines.append("")
        # 30% 概率插入代码块或列表，增强真实感
        r = rng.random()
        if r < 0.33:
            if topic.startswith(("Java", "Spring")):
                lines.append("```java")
                lines.append(f"// 示例：{topic} 相关代码")
                lines.append("public void handle() {")
                lines.append("    // TODO 业务逻辑")
                lines.append("}")
                lines.append("```")
            elif topic.startswith(("React",)):
                lines.append("```jsx")
                lines.append("function App() {")
                lines.append("  const [count, setCount] = useState(0);")
                lines.append("  return <button onClick={() => setCount(c => c + 1)}>{count}</button>;")
                lines.append("}")
                lines.append("```")
            elif topic.startswith(("Python",)):
                lines.append("```python")
                lines.append("def process(data):")
                lines.append("    return [x for x in data if x > 0]")
                lines.append("```")
            elif topic.startswith(("MySQL", "Redis")):
                lines.append("```sql")
                lines.append("-- 常用查询示例")
                lines.append("SELECT id, title, create_time")
                lines.append("FROM know_posts")
                lines.append("WHERE status = 'published' ORDER BY create_time DESC LIMIT 10;")
                lines.append("```")
            lines.append("")
        elif r < 0.6:
            lines.append("- 要点一：理解基本概念是后续深入学习的基础")
            lines.append("- 要点二：多动手实践，纸上得来终觉浅")
            lines.append("- 要点三：善用官方文档与高质量博客")
            lines.append("")
        # 加粗/强调
        lines.append(f"**总结**：{p[:18]}……")
        lines.append("")
    lines.append("---")
    lines.append("")
    lines.append(rng.choice(MD_OUTRO))
    md_text = "\n".join(lines)
    # description <= 50 字
    first_sentence = chosen[0]
    desc = first_sentence[:50]
    return md_text, desc


def random_time(base: dt.datetime, days_back_max: int, days_back_min: int = 0):
    delta = rng.randint(days_back_min, days_back_max)
    sec = rng.randint(0, 86400 - 1)
    return base - dt.timedelta(days=delta, seconds=sec)


# ----------------------------------------------------------------------------- 生成器
def gen_users(n: int):
    pre_hashed = bcrypt.hashpw(DEFAULT_PASSWORD.encode(), bcrypt.gensalt(rounds=BCRYPT_ROUNDS)).decode()
    users = []
    for i in range(1, n + 1):
        created = random_time(BASE_DATE, days_back_max=365, days_back_min=30)
        n_tags = rng.sample(USER_TAGS_POOL, k=rng.randint(2, 4))
        users.append({
            "phone": f"138{i:08d}",
            "email": f"user{i}{SEED_EMAIL_DOMAIN}",
            "password_hash": pre_hashed,  # 所有测试用户同一密码，hash 复用（合法）
            "nickname": f"{rng.choice(NICKNAME_ADJ)}{rng.choice(NICKNAME_NOUN)}{i:02d}",
            "avatar": f"https://picsum.photos/seed/ruuwen-u{i}/200/200",
            "bio": rng.choice(BIO_POOL),
            "zg_id": f"seed-zg-{i:04d}",
            "gender": rng.choice(["male", "female", "unknown"]),
            "birthday": random_time(BASE_DATE, days_back_max=365 * 28, days_back_min=365 * 18).date(),
            "school": rng.choice(SCHOOLS),
            "tags_json": json.dumps(n_tags, ensure_ascii=False),
            "created_at": created,
            "updated_at": created + dt.timedelta(seconds=rng.randint(0, 86400 * 5)),
        })
    return users


def gen_posts(n: int, creator_ids: list):
    posts = []
    files = []  # (filename, md_text, size, sha256)
    for _ in range(n):
        topic, tags, paragraphs = rng.choice(TOPICS)
        pid = Snowflake.next()
        md_text, desc = gen_md(pid, topic, paragraphs, tags)
        md_bytes = md_text.encode("utf-8")
        sha = hashlib.sha256(md_bytes).hexdigest()
        filename = f"{SEED_MD_PREFIX}{pid}.md"
        files.append((filename, md_text, len(md_bytes), sha))

        created = random_time(BASE_DATE, days_back_max=180, days_back_min=0)
        published = created + dt.timedelta(seconds=rng.randint(60, 86400 * 2))
        # 大多数 published+public，少量 draft / private 增加真实感
        roll = rng.random()
        if roll < 0.08:
            status, visible, pub_time = "draft", "public", None
        elif roll < 0.12:
            status, visible, pub_time = "published", "private", published
        else:
            status, visible, pub_time = "published", "public", published

        n_img = rng.choices([0, 1, 2, 3], weights=[45, 30, 15, 10])[0]
        img_urls = [f"https://picsum.photos/seed/ruuwen-p{pid}-{k}/800/500" for k in range(n_img)]

        posts.append({
            "id": pid,
            "tag_id": None,
            "tags": json.dumps(tags, ensure_ascii=False),
            "title": gen_title(topic),
            "description": desc,
            "content_url": f"/static/posts/{filename}",
            "content_object_key": f"{SEED_OBJKEY_PREFIX}{filename}",
            "content_etag": None,
            "content_size": len(md_bytes),
            "content_sha256": sha,
            "creator_id": rng.choice(creator_ids),
            "is_top": 1 if rng.random() < 0.05 else 0,
            "type": "image_text",
            "visible": visible,
            "img_urls": json.dumps(img_urls, ensure_ascii=False) if img_urls else None,
            "video_url": None,
            "status": status,
            "create_time": created,
            "update_time": published if pub_time else created,
            "publish_time": pub_time,
        })
    return posts, files


# ----------------------------------------------------------------------------- 落库
def clean_old(cur):
    print("[clean] 清理上一轮 seed 数据 ...")
    cur.execute("DELETE FROM know_posts WHERE content_object_key LIKE %s", (SEED_OBJKEY_PREFIX + "%",))
    kp = cur.rowcount
    cur.execute("DELETE FROM users WHERE email LIKE %s", ("%" + SEED_EMAIL_DOMAIN,))
    u = cur.rowcount
    print(f"[clean] 删除 {kp} 篇旧文章, {u} 个旧用户")
    # 清理旧 md
    removed = 0
    if MD_DIR.exists():
        for f in MD_DIR.glob(SEED_MD_PREFIX + "*.md"):
            f.unlink()
            removed += 1
    print(f"[clean] 删除 {removed} 个旧 md 文件")


def insert_users(cur, users):
    sql = """INSERT INTO users
        (phone, email, password_hash, nickname, avatar, bio, zg_id, gender,
         birthday, school, tags_json, created_at, updated_at)
        VALUES (%(phone)s, %(email)s, %(password_hash)s, %(nickname)s, %(avatar)s,
                %(bio)s, %(zg_id)s, %(gender)s, %(birthday)s, %(school)s,
                %(tags_json)s, %(created_at)s, %(updated_at)s)"""
    cur.executemany(sql, users)
    # 取回所有 seed 用户 id
    cur.execute("SELECT id FROM users WHERE email LIKE %s ORDER BY id", ("%" + SEED_EMAIL_DOMAIN,))
    return [r[0] for r in cur.fetchall()]


def insert_posts(cur, posts):
    sql = """INSERT INTO know_posts
        (id, tag_id, tags, title, description, content_url, content_object_key,
         content_etag, content_size, content_sha256, creator_id, is_top, type,
         visible, img_urls, video_url, status, create_time, update_time, publish_time)
        VALUES (%(id)s, %(tag_id)s, %(tags)s, %(title)s, %(description)s,
                %(content_url)s, %(content_object_key)s, %(content_etag)s,
                %(content_size)s, %(content_sha256)s, %(creator_id)s, %(is_top)s,
                %(type)s, %(visible)s, %(img_urls)s, %(video_url)s, %(status)s,
                %(create_time)s, %(update_time)s, %(publish_time)s)"""
    cur.executemany(sql, posts)


def write_md_files(files):
    MD_DIR.mkdir(parents=True, exist_ok=True)
    # 同时确保前端有 static 目录占位
    (MD_DIR.parent).mkdir(parents=True, exist_ok=True)
    for filename, md_text, _size, _sha in files:
        (MD_DIR / filename).write_text(md_text, encoding="utf-8")
    print(f"[md] 写入 {len(files)} 个正文到 {MD_DIR.relative_to(PROJECT_ROOT)}/")


# ----------------------------------------------------------------------------- main
def main():
    n_users = int(sys.argv[1]) if len(sys.argv) > 1 else 50
    n_posts = int(sys.argv[2]) if len(sys.argv) > 2 else 300
    print(f"== 生成 {n_users} 个用户 / {n_posts} 篇文章 ==")

    env = load_env(ENV_PATH)
    host, port, db = parse_mysql_dsn(env["SPRING_DATASOURCE_URL"])
    user = env["DB_USERNAME"]
    pwd = env["DB_PASSWORD"]
    print(f"[db] {user}@{host}:{port}/{db}")

    conn = pymysql.connect(host=host, port=port, user=user, password=pwd,
                           database=db, charset="utf8mb4", autocommit=False)
    try:
        with conn.cursor() as cur:
            clean_old(cur)
            t0 = time.time()
            print("[gen] 生成用户中（bcrypt strength=12，稍候）...")
            users = gen_users(n_users)
            user_ids = insert_users(cur, users)
            print(f"[db] 插入 {len(users)} 个用户，耗时 {time.time()-t0:.1f}s，user_id 范围 {min(user_ids)}~{max(user_ids)}")

            t1 = time.time()
            posts, files = gen_posts(n_posts, user_ids)
            insert_posts(cur, posts)
            print(f"[db] 插入 {len(posts)} 篇文章，耗时 {time.time()-t1:.1f}s")

            write_md_files(files)
        conn.commit()
        print("[ok] 事务已提交")

        # 简单校验
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM users WHERE email LIKE %s", ("%" + SEED_EMAIL_DOMAIN,))
            u_cnt = cur.fetchone()[0]
            cur.execute("SELECT COUNT(*) FROM know_posts WHERE content_object_key LIKE %s", (SEED_OBJKEY_PREFIX + "%",))
            p_cnt = cur.fetchone()[0]
            cur.execute("""SELECT status, COUNT(*) FROM know_posts
                           WHERE content_object_key LIKE %s GROUP BY status""", (SEED_OBJKEY_PREFIX + "%",))
            stat = cur.fetchall()
        print(f"[verify] users={u_cnt}, know_posts={p_cnt}, 状态分布={dict(stat)}")
        print(f"[done] 测试账号统一密码：{DEFAULT_PASSWORD}，邮箱后缀 {SEED_EMAIL_DOMAIN}")
    except Exception as e:
        conn.rollback()
        print(f"[error] 失败已回滚: {e}")
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
