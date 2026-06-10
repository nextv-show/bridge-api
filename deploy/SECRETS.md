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

CD（`.github/workflows/deploy.yml`）已把同组共享密钥改为 **GitHub Actions Secret 单一来源**：job 级 `env:` 读取 repo secret → 各 ssh 步骤经 `envs:` 转发到远端 → 远端 `upsert_secret` 幂等写入对应服务的 app.env。同组各服务读同一个 secret，天然逐字节一致。

`upsert_secret` 的三态语义（每次部署都执行，非仅首次 provision）：
- **repo secret 已设** → 把该值 upsert 进 app.env（覆盖旧行）：**现网每次部署自动对齐**，无需手动维护窗口；
- **repo secret 未设 + 该行已存在** → **原样保留**，绝不冲掉手动轮换/运维填的值（如 2026-06-09 手动轮换的 user/asset 强密钥）；
- **repo secret 未设 + 该行缺失** → 写同组占位（保证同组仍匹配、可启动）。

**需在仓库 Settings → Secrets and variables → Actions 配置的 repo secret：**

| Repo Secret | 注入到的服务 | 不配置的后果 |
|---|---|---|
| `JWT_SECRET` | user-service · asset-service | 回退占位 `CHANGE-ME-set-repo-secret-JWT_SECRET-...`（两端**同串**，仍匹配，但是公开弱值） |
| `H5_JWT_SECRET` | cend + 7 个 H5 消费方 | 回退 yml dev 默认（全组同串、匹配，但弱） |
| `S2S_TOKEN` | 全部后端服务 | 回退 `change-me-s2s-shared-token`（弱） |
| `ADMIN_JWT_SECRET` | admin-service | 回退 `sanshuiyuan-admin-jwt-prod-key-...`（弱） |

> 生成值：`openssl rand -hex 48`，在上面后台粘贴为对应 secret 的值。配置后，**下次任意一次部署**即把强值 upsert 到现网各服务（含已存在的 app.env），无需手动维护窗口。
> ⚠️ 轮换即生效：因 upsert 每次部署执行，改了 repo secret 值 → 下次部署同组全部换新 → 存量 token 失效（user/asset 改 `JWT_SECRET` 则用户需重登，小程序自动重登恢复）。轮换请挑窗口。
> ess-service 例外：其步骤 app.env 缺失即 FATAL、不自动建，且未接 upsert，须运维手动预置/对齐（含 `H5_JWT_SECRET`=cend 同值、`S2S_TOKEN`=全链路同值）。

## 待办 / 加固建议

- ess-service 尚未接入 `upsert_secret`（其步骤在 app.env 缺失时 FATAL、不自动建）。若要让 ess 也随 repo secret 自动对齐 `H5_JWT_SECRET`/`S2S_TOKEN`，需单独在其步骤加 upsert（注意它要求 app.env 必须已存在）。
