# 部署步骤

**仍部署原来的 `49.233.182.250` 服务器时，使用 [原服务器一键更新](EXISTING-SERVER.md)：`sudo python3 deploy/update_existing.py --apply`。不要重新初始化线上数据库。** 下文用于新环境安装或另行规划的数据迁移。

从后端仓库根目录操作。另两个仓库可独立克隆；下文跨仓库命令假设它们位于同级 `heluojia-admin`、`heluojia-miniapp` 目录。公共 SQL 用于全新安装；接管现有订单时使用独立交接的私密备份，先读末尾“现有业务迁移”。

## 1. 环境

准备 JDK 8、Maven 3.9.x、Node.js 22 LTS、npm、MySQL 8.0/8.4、Redis 6/7、Nginx、Python 3.10+ 和 MySQL 命令行客户端。服务器启用中文字体（例如 Noto Sans CJK），用于海报中文绘制。构建机可以和运行服务器分开；线上只需要 JRE 8、数据库、Redis、Nginx。

后端默认端口 18080。MySQL/Redis 仅开放本机或内网，Nginx 对外提供 HTTPS。`JAVA_HOME` 指向 JDK 8；不要直接使用未验证的 JDK 17/21 替代当前后端构建环境。

## 2. 初始化数据库

1. 根据 `database/00-create-database.sql.example` 创建 **空库** 和账号，替换模板密码。账号需要对该库有建表、改表及读写权限，后端启动会进行版本兼容升级。
2. 复制 `deploy/mysql-client.cnf.example` 为仓库外的 `mysql-client.cnf`，填写连接参数；Linux 设置 `chmod 600`。
3. 执行：

```sh
python -m pip install -r deploy/requirements.txt
python deploy/init_database.py --database meir_shop --defaults-file /secure/path/mysql-client.cnf
```

Windows 可用 `--mysql "C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe"` 指定客户端。工具按 01、02、03 导入 SQL，然后提示设置 **12～20 位**管理员密码，登录账号为 `admin`。没有默认通用密码。向非空库执行会直接拒绝，不会清表。

如果选择手工导入：依次导入 `database/01-schema.sql`、`02-system.sql`、`03-catalog.sql`。此时 `admin` 仍禁用；应通过 BCrypt 设置真实密码后再启用，推荐使用上述工具一次完成。不要导入 `bootstrap` 目录，也不要在现网执行初始化 SQL。

新安装包含七款正式商品，库存为 0。后台按实际库存入库后才能下单；未保留 0.01 元测试商品、已有用户订单、领券和限时活动。

## 3. 配置并构建三端

```sh
mvn clean verify
npm --prefix ../heluojia-admin ci
cp ../heluojia-admin/.env.production.example ../heluojia-admin/.env.production.local
npm --prefix ../heluojia-admin run build:prod
npm --prefix ../heluojia-miniapp ci
cp ../heluojia-miniapp/.env.example ../heluojia-miniapp/.env.local
```

将 `../heluojia-miniapp/.env.local` 的 `VITE_SHOP_API_BASE` 改为目标 API 域名，例如 `https://api.example.com`，不要加 `/prod-api` 或末尾 `/`。然后：

```sh
npm --prefix ../heluojia-miniapp run build:h5
npm --prefix ../heluojia-miniapp run build:mp-weixin
```

更换小程序主体时，修改 `../heluojia-miniapp/manifest.json` 内 `mp-weixin.appid`，并保持与服务端 `WECHAT_APP_ID` 一致。AppID 不是 AppSecret；小程序源码与构建产物不得包含 AppSecret、商户私钥或 APIv3 密钥。

后台生产接口默认 `/prod-api`；同域 Nginx 代理已在模板中提供。需要覆盖后台配置时复制 `../heluojia-admin/.env.example` 为 `../heluojia-admin/.env.production.local`。本地开发可用 `npm --prefix ../heluojia-admin run dev` 和 `npm --prefix ../heluojia-miniapp run dev:h5`，两个前端端口分别为 15173、15174。

后端仓库已包含 `database/bootstrap`，单独克隆即可构建；不要仅复制 `ruoyi-admin` 子模块。

## 4. 放置服务器文件

以下以 Linux `/opt/meir-shop` 为例：

```text
/opt/meir-shop/
  app/ruoyi-admin.jar
  admin/                       ← ../heluojia-admin/dist 全部内容
  h5/                          ← ../heluojia-miniapp/dist/build/h5 全部内容
  application-server.properties
  server.env
  runtime/assets/               ← assets/assets 全部内容
  runtime/profile/              ← assets/profile 全部内容
  runtime/private/              ← 空目录；私密资料由程序写入
  runtime/logs/
  wechat-pay/                   ← 单独交接的支付证书/密钥
```

创建专用用户及目录，把 `deploy/application-server.properties.example` 和 `deploy/server.env.example` 分别复制到上面对应文件。填写数据库、Redis、Token 随机密钥（可用 `openssl rand -hex 32` 生成），然后设置所有者与权限：

```sh
sudo useradd --system --home /opt/meir-shop --shell /usr/sbin/nologin meir-shop
sudo mkdir -p /opt/meir-shop/{app,admin,h5,runtime/assets,runtime/profile,runtime/private,runtime/logs,wechat-pay}
# 文件复制完成后：
sudo chown -R meir-shop:meir-shop /opt/meir-shop
sudo chmod 600 /opt/meir-shop/server.env /opt/meir-shop/application-server.properties
sudo chmod 700 /opt/meir-shop/runtime/private /opt/meir-shop/wechat-pay
```

静态目录 `admin`、`h5` 及其上级目录应允许 Nginx 用户读取/遍历；私密目录不要配置成 Nginx 静态目录。商品图片清单在 `assets/manifest.json`，数据库和这些图片需要一起交付。

`server.env` 按 systemd EnvironmentFile 格式填写，不加 `export`，密码含空格时用引号。复制 `deploy/meir-shop.service` 到 `/etc/systemd/system/`，检查 Java 路径，然后启动：

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now meir-shop
sudo journalctl -u meir-shop -n 100 --no-pager
curl http://127.0.0.1:18080/shop/app/bootstrap
```

## 5. 域名和微信配置

1. 将 `deploy/nginx.conf.example` 中两个域名和证书路径替换为真实值，放入 Nginx 配置目录，执行 `nginx -t`，通过后重载。一个域名提供后台及 API，另一个提供 H5。
2. 在小程序平台配置 API 域名为 request、uploadFile、downloadFile 合法域名。微信开发者工具导入 `../heluojia-miniapp/dist/build/mp-weixin`，由有权限的成员上传体验版。
3. 服务端填写 `WECHAT_APP_ID`、`WECHAT_APP_SECRET`；没有这些信息只能浏览公共页面，微信登录不可用。体验版海报码用 `WECHAT_CODE_VERSION=trial`，正式版改为 `release`。
4. 接通真实支付时把三份文件放入 `wechat-pay`：`apiclient_cert.pem`、`apiclient_key.pem`、`pub_key.pem`，限制读取权限。填写商户号、APIv3 密钥、公钥 ID，设置 `WECHAT_PAY_ENABLED=true`。
5. 支付回调 `https://API域名/shop/wechat-pay/notify`，退款回调 `https://API域名/shop/wechat-pay/refund-notify`；Nginx 模板已保留这两个地址。回调需要公网 HTTPS 可达，不能要求后台登录。
6. 重启后端后检查启动日志。支付启用但证书/密钥不匹配会阻止启动；不要关闭校验。新环境默认关闭真实支付和模拟付款。
7. 后台核对配送、售后地址、商家联系信息、隐私协议和 H5 分享地址。安装库 `rules.shareBaseUrl` 使用 `https://shop.example.com`，请按实际 H5 域名修改。原品牌公开协议文本可作为既有配置查看，但更换主体需同步真实运营资料。

同一商户迁移服务器时，先完成新环境只读核对，再切换正式域名和回调，避免两个业务库同时处理相同商户订单。

## 6. 验收

- 后台新密码可登录、商城管理菜单可打开。
- 首页图片正常；七款商品 C 端标价为 318 / 328 / 268 / 348 / 398 / 238 / 398 元。
- 普通用户只按零售购买；分销申请审核通过后从“专属拿货”进入才有拿货价。
- 库存入库后，用本人测试账号完成一笔小额真实支付、取消未付订单、仅退款，检查订单流转和库存恢复。
- 检查商品分享封面、客服、收货地址、订单/售后、发票入口和后台订单查询。
- 浏览器刷新后台路由不应 404；H5 和小程序均指向本次部署域名。

`mvn verify` 默认执行无需数据库的单元测试。需要运行本地数据库集成测试时，将测试库连接配置写入仓库外的 properties 文件（`spring.datasource.druid.master.url/username/password`），在专用本地测试库初始化后执行 `mvn verify -Dwechat.test.properties=/absolute/path/test.properties`。这些测试只允许 localhost/127.0.0.1，禁止指向生产库。

## 7. 现有业务迁移与备份

完整迁移还需要单独交接：完整数据库 SQL、`runtime/assets`、`runtime/profile`、`runtime/private`、支付证书和外部配置。私密资料不进 GitHub；本次整理的副本在仓库外归档目录。快照是整理时读取的备份，正式迁移应在停写窗口再取一致快照，不能直接当作之后的新订单最终数据。

恢复到空库时导入 **完整私密 SQL**，不要再导入本仓库 01～03。复制对应上传文件，配置目标数据库与 Redis，确认登录缓存隔离，最后启动同版本后端，让兼容升级逻辑正常执行。先用只读查询比对订单数、退款数、支付/退款金额及库存，再切换域名。

后续可设置 `shop.backup.enabled=true` 启用应用自带备份；先核对 `shop.backup.mysqldump` 的真实路径及备份目录权限，并配置异机保存。发布前另外备份 JAR、前端静态文件、数据库与上传文件；回滚不得只回滚数据库而丢失之后发生的付款。
