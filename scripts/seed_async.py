#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
补齐异步链路数据（接 seed_data.py 生成的批量文章）：
  1. 上传 Markdown 正文到 OSS，把 content_url 改成 OSS 公开 URL
     → 后端 SearchIndexService/RagIndexService 的 fetchContent 才能拉到正文
  2. 往 outbox 表写 KnowPostInserted 事件
     → Canal 监听 binlog → Kafka canal-outbox → CanalOutboxConsumerSearch
       同时调 SearchIndexService.upsertKnowPost(文本索引) + RagIndexService.rebuildSinglePost(向量索引)
  3. 直连 Redis 写文章计数(位图 bm:* + SDS cnt:v1:*)，造随机 like/fav
  4. 直连 Redis 写排行榜(按作者 ownerId 累计 like 入日榜)：ZSet lb:zset:* + ucnt lb:ucnt:* + 线段树 lb:seg:*

数据结构与后端完全对齐：
  - CounterKeys/CounterSchema: cnt:v1:{etype}:{eid} = 20B 大端 Int32×5 (idx1=like, idx2=fav)
  - BitmapShard: bm:{metric}:{etype}:{eid}:{uid//32768}, bit = uid%32768
  - LeaderboardKeys/SegmentTreeServiceImpl: lb:zset/seg/ucnt/agg:{rankName}, rankName = like:daily:{yyyyMMdd}
  - 线段树区间二分: min=1, max=100000, bucket=100, field = "lower-upper"

幂等：重跑会清理上一轮 seed 文章的 Redis 计数 key 与当天日榜 key 后重建；
      OSS put_object 覆盖写；outbox 为追加日志（重复事件由下游 upsert/重建幂等吸收）。
"""
import os
import re
import sys
import json
import struct
import random
from pathlib import Path

import pymysql
import oss2
import redis

PROJECT_ROOT = Path(__file__).resolve().parent.parent
ENV_PATH = PROJECT_ROOT / ".env"
MD_DIR = PROJECT_ROOT / "frontend" / "public" / "static" / "posts"

SEED_OBJ_PREFIX = "seed/posts/"        # seed_data.py 写入的 content_object_key 前缀
OSS_OBJ_PREFIX = "posts/"              # OSS 内对象前缀
ENTITY_TYPE = "knowpost"
SCHEMA_ID = "v1"
IDX_LIKE, IDX_FAV = 1, 2
TODAY = "20260707"                     # 排行榜日榜日期（今天）
RANK_NAME = f"like:daily:{TODAY}"
SEG_MIN, SEG_MAX, SEG_BUCKET = 1, 100000, 100

rng = random.Random(20260707)


def load_env(path: Path) -> dict:
    env = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip().strip('"').strip("'")
    return env


def parse_mysql(url: str):
    m = re.search(r"mysql://([^:/?#]+)(?::(\d+))?/([^?#/]+)", url)
    return m.group(1), int(m.group(2) or 3306), m.group(3)


def seg_path_fields(score: int) -> dict:
    """复刻 SegmentTreeServiceImpl.addPathDelta(score, +1)。返回 {field: delta}。"""
    if score <= 0:
        return {}
    ns = min(max(score, SEG_MIN), SEG_MAX)
    lower, upper = SEG_MIN, SEG_MAX
    fields = {}
    while (upper - lower + 1) > SEG_BUCKET:
        f = f"{lower}-{upper}"
        fields[f] = fields.get(f, 0) + 1
        mid = lower + (upper - lower) // 2
        if ns <= mid:
            upper = mid
        else:
            lower = mid + 1
    f = f"{lower}-{upper}"
    fields[f] = fields.get(f, 0) + 1
    return fields


def main():
    env = load_env(ENV_PATH)
    host, port, db = parse_mysql(env["SPRING_DATASOURCE_URL"])

    # OSS
    ak, sk = env["OSS_ACCESS_KEY_ID"], env["OSS_ACCESS_KEY_SECRET"]
    bucket_name = env["OSS_BUCKET"]
    endpoint = env["OSS_ENDPOINT"].replace("https://", "").replace("http://", "").rstrip("/")
    public_domain = env.get("OSS_PUBLIC_DOMAIN", "")
    if public_domain and not public_domain.startswith("http"):
        public_domain = "https://" + public_domain
    auth = oss2.Auth(ak, sk)
    bucket = oss2.Bucket(auth, endpoint, bucket_name)
    print(f"[oss] endpoint={endpoint} bucket={bucket_name} domain={public_domain}")

    # Redis（保持 bytes 模式，SDS 是二进制）
    r = redis.Redis(
        host=env["REDIS_HOST"], port=int(env["REDIS_PORT"]),
        password=env.get("REDIS_PASSWORD") or None,
        db=int(env.get("REDIS_DATABASE", "0")),
        decode_responses=False,
    )
    r.ping()
    print(f"[redis] 连接 OK @ {env['REDIS_HOST']}:{env['REDIS_PORT']}")

    conn = pymysql.connect(host=host, port=port, user=env["DB_USERNAME"],
                           password=env["DB_PASSWORD"], database=db,
                           charset="utf8mb4", autocommit=False)
    try:
        with conn.cursor() as cur:
            cur.execute("""SELECT id, creator_id FROM know_posts
                           WHERE content_object_key LIKE %s
                             AND status='published' AND visible='public'""",
                        (SEED_OBJ_PREFIX + "%",))
            posts = cur.fetchall() or []
        print(f"[db] 待处理 seed 文章(published+public): {len(posts)}")
        if not posts:
            print("[skip] 没有符合条件的文章，退出"); return

        # ---------- 1. 上传 OSS 正文 + 更新 content_url ----------
        print("[1/4] 上传正文到 OSS 并更新 content_url ...")
        up = 0
        for i, (pid, creator) in enumerate(posts, 1):
            md_file = MD_DIR / f"seed-{pid}.md"
            if not md_file.exists():
                print(f"  [warn] 缺 md: {md_file.name}，跳过"); continue
            obj_key = f"{OSS_OBJ_PREFIX}seed-{pid}.md"
            bucket.put_object(obj_key, md_file.read_bytes(),
                              headers={"Content-Type": "text/markdown; charset=utf-8"})
            url = f"{public_domain.rstrip('/')}/{obj_key}"
            with conn.cursor() as cur:
                cur.execute("UPDATE know_posts SET content_url=%s WHERE id=%s", (url, pid))
            up += 1
            if i % 50 == 0:
                conn.commit(); print(f"  ... {i}/{len(posts)}")
        conn.commit()
        print(f"[1/4] OSS 上传完成: {up} 篇")

        # ---------- 2. 写 outbox 事件（驱动搜索+RAG）----------
        print("[2/4] 写 outbox KnowPostInserted 事件 ...")
        with conn.cursor() as cur:
            for pid, creator in posts:
                eid = rng.randrange(2 ** 62)
                payload = json.dumps({
                    "aggregateType": "knowpost", "aggregateId": pid,
                    "eventType": "KnowPostInserted",
                    "data": {"entity": "knowpost", "op": "insert", "id": pid},
                }, ensure_ascii=False)
                cur.execute("""INSERT INTO outbox
                    (id, aggregate_type, aggregate_id, type, payload, created_at)
                    VALUES (%s,'knowpost',%s,'KnowPostInserted',%s,NOW(3))""",
                            (eid, pid, payload))
        conn.commit()
        print(f"[2/4] outbox 写入 {len(posts)} 条 → Canal 将异步驱动 ES 文本索引 + 向量索引")

        # ---------- 3. Redis 文章计数（位图+SDS）----------
        print("[3/4] 清理旧计数 + 写 like/fav(位图+SDS) ...")
        for pid, creator in posts:
            eid = str(pid)
            for pat in (f"bm:like:{ENTITY_TYPE}:{eid}:*", f"bm:fav:{ENTITY_TYPE}:{eid}:*"):
                for k in r.scan_iter(match=pat, count=1000):
                    r.delete(k)
            r.delete(f"cnt:{SCHEMA_ID}:{ENTITY_TYPE}:{eid}",
                     f"agg:{SCHEMA_ID}:{ENTITY_TYPE}:{eid}")

        with conn.cursor() as cur:
            cur.execute("SELECT id FROM users WHERE email LIKE %s ORDER BY id", ("%@seed.ruiwen",))
            user_ids = [x[0] for x in cur.fetchall()]
        max_u = len(user_ids)

        author_likes = {}
        pipe = r.pipeline()
        for pid, creator in posts:
            eid = str(pid)
            like_n = rng.randint(0, min(50, max_u))
            fav_n = rng.randint(0, min(20, max_u))
            for uid in rng.sample(user_ids, like_n):
                pipe.setbit(f"bm:like:{ENTITY_TYPE}:{eid}:{uid // 32768}", uid % 32768, 1)
            for uid in rng.sample(user_ids, fav_n):
                pipe.setbit(f"bm:fav:{ENTITY_TYPE}:{eid}:{uid // 32768}", uid % 32768, 1)
            pipe.set(f"cnt:{SCHEMA_ID}:{ENTITY_TYPE}:{eid}", struct.pack(">5i", 0, like_n, fav_n, 0, 0))
            author_likes[creator] = author_likes.get(creator, 0) + like_n
        pipe.execute()
        print(f"[3/4] 计数写入完成；{len(author_likes)} 位作者累计获得 like")

        # ---------- 4. Redis 排行榜（ZSet+ucnt+线段树）----------
        print(f"[4/4] 写排行榜 {RANK_NAME} ...")
        zset_key = f"lb:zset:{RANK_NAME}"
        seg_key = f"lb:seg:{RANK_NAME}"
        for pat in (zset_key, seg_key, f"lb:ucnt:{RANK_NAME}:*", f"lb:agg:{RANK_NAME}:*"):
            for k in r.scan_iter(match=pat, count=1000):
                r.delete(k)
        seg_delta = {}
        pipe = r.pipeline()
        in_board = 0
        for owner, score in author_likes.items():
            if score <= 0:
                continue
            pipe.zadd(zset_key, {str(owner): score})
            pipe.hset(f"lb:ucnt:{RANK_NAME}:{owner}", "today", score)
            in_board += 1
            for f, d in seg_path_fields(score).items():
                seg_delta[f] = seg_delta.get(f, 0) + d
        pipe.execute()
        if seg_delta:
            pipe = r.pipeline()
            for f, d in seg_delta.items():
                pipe.hincrby(seg_key, f, d)
            pipe.execute()
        print(f"[4/4] 排行榜写入完成：{in_board} 位作者入榜，线段树区间 {len(seg_delta)} 个")

        # ---------- 验证 ----------
        print("\n== 即时验证 ==")
        pid0 = posts[0][0]
        val = r.get(f"cnt:{SCHEMA_ID}:{ENTITY_TYPE}:{pid0}")
        if val and len(val) == 20:
            v = struct.unpack(">5i", val)
            print(f"[verify] 文章 {pid0} 计数 SDS: like={v[1]} fav={v[2]}")
        print(f"[verify] 日榜 ZSet 成员数: {r.zcard(zset_key)}")
        top = r.zrevrange(zset_key, 0, 4, withscores=True)
        print(f"[verify] Top5 作者(分数): {[(m.decode(), int(s)) for m, s in top]}")
        print(f"[verify] 线段树区间数: {r.hlen(seg_key)}")
        print("\n[done] 搜索/向量索引由 Canal 异步处理，约 10–60s 后用 ES _count 复核。")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
