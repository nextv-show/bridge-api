# 三水元后端 — 生产密钥清单（Secret Manifest）

> 本文件只列**密钥名 / 归属 / 一致性约束 / 生成与轮换办法**，**绝不写入任何真实值**。
> 真实值的唯一存放处：生产服务器各服务的 `EnvironmentFile` —— `/opt/bridge-<svc>/app.env`（root 持有，已 `app.env.bak-*` 备份）。
> 仓库 `application.yml` 里的 `${VAR:default}` 仅为 **dev 占位**；CD（`.github/workflows/deploy.yml`）首次部署写入的是**占位骨架**，且仅在 `app.env` 不存在时写入——**生产真实值必须由运维手动覆写**。
> ⚠️ 占位即弱默认：凡未在 `app.env` 显式设置的密钥，进程会回退到 yml 弱默认值（公开可知）。**env 是唯一防线。**

## 一致性约束（同组必须逐字节相同）

| 组 | 密钥名 | 必须持有相同值的服务 | 算法/用途 |
|---|---|---|---|
| **用户态 JWT** | `JWT_SECRET` | `user-service`(签发) · `asset-service`(验签) | HS256，小程序/App access+refresh token。不一致 → asset 全量 401 |
| **H5 会话 JWT** | `H5_JWT_SECRET` | `cend-service`(签发) · `ess-service` · `matching-service` · `water-service` · `iot-gateway-service` · `logistics-service` · `settlement-service` · `evidence-worker` | HS256，H5/小程序 h5_token。与 `JWT_SECRET` **是两套独立密钥** |
| **管理后台 JWT** | `ADMIN_JWT_SECRET` | `admin-service` | HS256，console 管理员登录。独立 |
| **服务间调用** | `S2S_TOKEN` | 全部后端服务（user/asset/cend/ess/admin/matching/water/iot-gateway/logistics/settlement/evidence-worker） | 内网 S2S 鉴权头，全链路同值 |

> 同组改一处必须同步改全组并一起重启，否则该组内跨服务调用/验签立刻断。

## 独立密钥（各服务自签自验，无需互相匹配，但都必须是强随机值）

| 密钥名 | 服务 | 用途 / 备注 |
|---|---|---|
| `USER_REFERRAL_REF_ID_SECRET` | user-service | 推荐 ref_id 的 HMAC 签名密钥（小程序推荐链）。弱则可伪造任意 userId 的 ref_id |
| `H5_REFERRAL_REF_ID_SECRET` | cend-service | 同上，H5 推荐链。与 user 的独立 |
| `H5_AES_MASTER_KEY` | cend-service | 身份证号 AES-256 主密钥（32 字节 base64）。轮换需配套数据再加密，勿轻易动 |
| `DB_PASSWORD` | 全部连库服务 | MySQL root 口令（生产 docker `bridge-mysql`） |
| `WXPAY_API_V3_KEY` / `WXPAY_*` | asset-service · cend-service · settlement-service | 微信支付商户凭证（公钥模式，见钱包/认购支付记忆） |
| `ESS_SECRET_ID` / `ESS_SECRET_KEY` | ess-service | 腾讯电子签 API 凭证 |
| `OSS_ACCESS_KEY` / `OSS_SECRET_KEY` | ess-service | 对象存储（MinIO） |
| `ALIYUN_KYC_*` | cend-service | 阿里实名认证 |
| `WEBHOOK_SF_SECRET` / `WEBHOOK_JD_SECRET` | logistics-service | 顺丰/京东物流回调验签 |

## 生成办法

```bash
# JWT / S2S / ref_id 等对称密钥（≥256bit）：
openssl rand -hex 48        # 96 字符，纯 hex，sed/env 安全
# 需要 base64 的（如 H5_AES_MASTER_KEY=32 字节）：
openssl rand -base64 32
```

`JwtIssuer.padSecret`：密钥 UTF-8 不足 32 字节时右补零（故 hex48/base64-48 均满足强度）。

## 轮换流程（以 `JWT_SECRET` 为例，同组通用）

1. 生成一个新值：`NEW=$(openssl rand -hex 48)`。
2. **同组所有服务**的 `/opt/bridge-<svc>/app.env` 把该密钥改为同一个 `NEW`（先 `cp -a app.env app.env.bak-<date>`）。
   - 校验同值（不打印明文）：`md5sum <(grep '^JWT_SECRET=' a/app.env) <(grep '^JWT_SECRET=' b/app.env)` 两行 md5 必须相等。
3. 一起重启：`systemctl restart bridge-user-service bridge-asset-service`。
4. 验证：用旧值铸的 token 调受保护接口应返回 401/403（旧 token 失效即生效）。
5. 影响：存量 access/refresh token 全失效；小程序 `ensureAuth` 自动重新登录恢复，用户下次打开即可。

## 变更记录

- **2026-06-09**：`JWT_SECRET` 经实测发现生产仍为 yml 弱默认（用 `change-me-...` 铸 token 可 200），已轮换为 `openssl rand -hex 48` 强随机值，`user-service` 与 `asset-service` 同值并重启，旧 token 已全 403。同时把 `JWT_SECRET` 补进本清单与 CD 骨架（user-service 占位改为"需强随机+须与 asset 一致"；asset-service 骨架此前**完全缺失** `JWT_SECRET` 行，已补）。详见父仓记忆 `jwt-auth-security`。

## CD 注入共享密钥（已实施）

CD（`.github/workflows/deploy.yml`）把同组共享密钥改为 **GitHub Actions Secret 单一来源**：job 级 `env:` 读取 repo secret → 各 ssh 步骤经 `envs:` 转发到远端 → 远端 `seed_secret` **按需补齐**对应服务的 app.env。

`seed_secret` 是 **seed-on-absent（仅缺失时写）**，**不做就地轮换**：
- **该键已在 app.env 存在** → **一律不动**（无论 repo secret 是否设置）。现网各服务的密钥不会被 CD 改写。
- **该键缺失 + repo secret 已设** → 写 repo secret 值（新建/灾备 provision、或某服务漏配该键时，自动补成与同组一致的强值）。
- **该键缺失 + repo secret 未设** → 写该键的 application.yml 默认值（= 不接此功能时该服务本就会用的值 → 行为零变化）。

> **为什么不在 CD 里就地轮换？** CD 各服务是顺序写 env + 重启的。若就地改写共享密钥：① 签发方（如 cend/user）先于验签方重启 → 中间窗口新 token 被旧验签方 401；② 某步骤健康检查失败回滚只还原 jar、不还原 env → 留下持久 split-brain。故轮换必须用下方手动 SOP（一次性对全组改值 + 协调重启），CD 只负责"provision 时把缺失的密钥补齐到同组一致"。

**需在仓库 Settings → Secrets and variables → Actions 配置的 repo secret（供新建/灾备 provision 落强值）：**

| Repo Secret | 注入到的服务 | 缺失且未配置时的回退（= yml 默认，inert） |
|---|---|---|
| `JWT_SECRET` | user-service · asset-service | `change-me-in-production-at-least-256-bits-long!!`（两端 yml 默认本就相同→同串匹配） |
| `H5_JWT_SECRET` | cend(签发) + 6 消费方 + ess | `dev-h5-jwt-secret-please-override-in-prod-0001`（全组 yml 默认相同→匹配） |
| `S2S_TOKEN` | 全部 11 个后端服务 | `local-dev-static-token`（统一值；注 1） |
| `ADMIN_JWT_SECRET` | admin-service | `sanshuiyuan-admin-jwt-prod-key-2026-please-change-in-production`（单服务自签自验，任意值可用） |

> ⚠️ **配置 repo secret 不会改动现网**（现网各 app.env 都已有这些键 → seed-on-absent 命中"已存在"分支不动）。repo secret 仅在**键缺失**时生效——即新机/灾备 provision，或个别服务漏配某键。
> 配置时请把 repo secret 值设为**与现网当前同组值一致**（这样新补齐的服务与现网匹配）；若要换成全新强值，按下方「轮换流程」对全组手动改 + 协调重启，别只设 repo secret。
> ess-service：app.env 仍须运维**预置**（缺失即 FATAL、不自动建）；但已在 FATAL 守卫之后接入 `seed_secret`，故其漏配的 `H5_JWT_SECRET`/`S2S_TOKEN` 也会按需补齐。
>
> 注 1（S2S 回退取值）：各服务 yml 默认并不一致（核心服务 user/asset/admin/cend/ess=`local-dev-static-token`，6 个消费方=`dev-s2s-shared-token`）；统一回退取 `local-dev-static-token`。对**现网无影响**（11 个服务 app.env 均已显式写 `S2S_TOKEN`，命中"已存在"分支不动）；统一值仅作用于全新/灾备 provision，反而修掉了旧骨架"各服务回退到不同 S2S→跨服务 401"的 split-brain。
