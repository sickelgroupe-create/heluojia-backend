# 原服务器一键更新

适用：现有服务器 `49.233.182.250`，服务 `meir-shop`，后台/API `meirht.okam.top`，H5 `meir.okam.top`。继续使用这三个 GitHub 仓库，不创建新业务库、不导入安装 SQL、不更换微信支付配置。

已于 2026-09-22 在原服务器完成一次完整更新，见 [实测记录](EXISTING-SERVER-VERIFICATION.md)。

## 同事第一次操作

通过自己的 SSH 授权以 root 登录原服务器；普通运维账号有 sudo 权限时，先执行 `sudo -i` 切换到 root。源码、日志和备份目录仅授权运维人员访问。首次拉取后端：

```sh
sudo mkdir -p /opt/meir-shop/source
cd /opt/meir-shop/source
sudo git clone https://github.com/sickelgroupe-create/heluojia-backend.git
cd heluojia-backend
sudo python3 deploy/update_existing.py --apply
```

以后更新，在该仓库内执行：

```sh
sudo git pull --ff-only && sudo python3 deploy/update_existing.py --apply
```

后台和小程序仓库由脚本按 `deploy/existing-server.json` 中的提交号自动拉取，不需要手工复制三个项目，也不需要把密码写进命令。部署失败会返回非零状态，输出对应日志目录；不要把运行日志、数据库备份和私密文件提交到 GitHub。

## 自动完成什么

1. 检查原服务、112 张业务表、现有数据库连接文件、支付配置、目录及剩余磁盘；环境不符时拒绝更新。
2. 使用服务器已有的 JDK 8 和 Maven。首次自动下载官方 Node 22.23.2，校验固定 SHA-256，放在 `/opt/meir-shop/build-tools`，不更改系统 Java/Node 默认版本。
3. 构建后端并运行默认测试，逐份核对 JAR 内的 24 份启动 SQL；按锁文件安装前端依赖，构建后台、H5 和微信小程序。
4. 备份原 JAR、网站、数据库、上传文件和私密配置到 `/opt/meir-shop/backups/github-*`；备份失败不切换服务。
5. 短暂重启后端、校验接口，再更新后台与 H5，最后切换网站首页。保留旧静态资源供已有浏览器会话使用。
6. 核对公开页面、付款能力、原配置哈希及既有订单成交金额。切换失败则恢复旧程序和网站，**不回滚业务数据库**，避免丢失已发生交易。

脚本使用现有 `/opt/meir-shop/mysql-client.cnf` 和 systemd 服务配置；支付密钥、Redis 配置、数据库账号无需重新填写。新版本自身的兼容数据库升级仍由后端启动逻辑执行；本脚本不会运行 `init_database.py` 或 `database/01～03`。

## 只检查 / 只构建

```sh
sudo python3 deploy/update_existing.py --check
sudo python3 deploy/update_existing.py --build-only
```

`--check` 不修改服务或文件。`--build-only` 在独立目录拉取源码并构建，可能安装独立 Node 工具，不切换现网、不修改业务库。构建和更新结果分别写入本次目录下 `build-result.json` / `deployment-result.json`；最近一次成功更新另记录到 `/opt/meir-shop/last-deployment.json`。

网络需要能访问 GitHub、Maven、npm 和 Node 官方下载站。现有数据库/服务器的访问权限须由负责人交接；公开源码不包含这些凭据。

## 三仓库版本与后续开发

后端使用当前工作副本的已提交 HEAD；有未提交文件时拒绝部署。后台和小程序按 `existing-server.json` 固定提交号构建，避免三个仓库更新时混入未验证版本。

同事修改前端后：先测试并推送对应仓库，再将其完整提交号更新到该文件的 `admin_revision` / `miniapp_revision`，提交推送后端仓库，最后在服务器拉取并执行同一条更新命令。

## 微信体验版

服务器更新会生成 `miniapp-upload.tar.gz`，路径在脚本结束输出中。下载并解压，用微信开发者工具导入其中的 `mp-weixin`，由有开发权限的微信上传，再在公众平台设置体验版。微信扫码登录、上传和体验版设置不包含在服务器自动更新内。

## 全新服务器

此入口专门更新原服务器，不适用于空服务器。全新安装或完整数据迁移仍按 [部署说明](README.md) 操作，不要把公共初始化库覆盖到现有商城。
