# heluojia-backend

赫洛嘉商城后端服务，为运营后台、微信小程序及 H5 提供统一业务 API。

## 项目简介

提供微信登录、固定零售价购物、分销商审核与专属拿货、购物车、库存预占、订单流转、微信支付及退款、优惠券、积分、售后、发票、客服、运营权限和报表等功能。保留旧企业采购订单处理能力，新申请及新采购入口已关闭。

本仓库可以独立构建，包含完整 **112 张表**的建表 SQL、基础权限数据、七款正式商品配置及 **18 个公共素材文件**。公共安装库不含现网用户、订单、支付凭据，库存从 0 开始；正式业务迁移需要单独交接私密备份。

## 技术栈

- Java 8、Spring Boot 2.5.15、Spring Security 5.7.14
- Maven 多模块：ruoyi-admin、ruoyi-framework、ruoyi-system、ruoyi-common
- MyBatis XML Mapper、PageHelper、Druid、MySQL 8.0/8.4、Redis
- 微信支付 APIv3：商户签名、通知验签、退款和查询补偿
- JUnit 5、Mockito；部署辅助工具为 Python 3.10+，Nginx 和 systemd 提供生产运行模板

不依赖另两个仓库的文件；本项目使用 MyBatis，而非 MyBatis Plus。

## 关联仓库

| 项目 | 说明 | GitHub |
| --- | --- | --- |
| heluojia-backend | 后端 API、业务逻辑、数据库与部署资料 | [heluojia-backend](https://github.com/sickelgroupe-create/heluojia-backend) |
| heluojia-admin | 商品、订单、库存及财务运营管理后台 | [heluojia-admin](https://github.com/sickelgroupe-create/heluojia-admin) |
| heluojia-miniapp | 微信小程序与 H5 用户端 | [heluojia-miniapp](https://github.com/sickelgroupe-create/heluojia-miniapp) |

## 快速启动

**继续部署原服务器**：同事拉取本仓库后，在原服务器执行 `sudo python3 deploy/update_existing.py --apply`，自动拉取配套前端、构建、备份、更新和检查，复用原数据库及支付配置。首次目录准备、后续更新及微信体验版边界见 [原服务器一键更新](deploy/EXISTING-SERVER.md)。以下步骤用于全新本地环境。

准备 JDK 8、Maven 3.9.x、MySQL 8.0/8.4、Redis。以下命令在本仓库根目录执行。

1. 按 [数据库说明](database/README.md) 创建空数据库及账号；将 `deploy/mysql-client.cnf.example` 复制到仓库外，填入本机数据库连接参数。
2. 初始化 112 张表及基础数据，同时设置自己的管理员密码：

```bash
python -m pip install -r deploy/requirements.txt
python deploy/init_database.py --database meir_shop --defaults-file /secure/path/mysql-client.cnf
```

3. 复制本地配置，填写数据库和 Redis 参数。真实密码、Token 随机密钥只写入本地配置或环境变量，不提交到 Git。

```bash
cp deploy/application-local.properties.example application-local.properties
mkdir -p runtime/assets runtime/profile runtime/private
cp -R assets/assets/. runtime/assets/
cp -R assets/profile/. runtime/profile/
mvn clean verify
java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.config.additional-location=file:./application-local.properties
```

Windows 可使用 PowerShell 的 `Copy-Item`、`New-Item` 完成配置和目录复制，Maven 与 Java 命令相同。默认 API 地址 `http://127.0.0.1:18080`；公共检查接口 `/shop/app/bootstrap`。管理员账号为 `admin`，密码由初始化工具设置，没有通用默认密码。

微信登录需填写自己的 AppID / AppSecret。真实支付默认关闭，启用前必须填写商户配置及服务器证书路径。普通构建和公共页面浏览不需要支付凭据。生产部署、HTTPS、回调地址和三端文件对应关系见 [部署说明](deploy/README.md)。

## 数据库与购买规则

`database/01-schema.sql`、`02-system.sql`、`03-catalog.sql` 依次用于空库安装。`database/bootstrap/` 的 24 份启动脚本由 Maven 自动打包；不要手工重复导入，也不要对现网非空库执行初始化。

C 端标价固定，会员等级不改变零售标价；优惠活动、优惠券和积分可独立影响实付款。B 端只对审核通过的分销商开放，专属拿货按档位计价；企业采购保留历史。分销身份不会改变零售入口的标价。

## 项目结构

```text
ruoyi-admin/       启动入口、商城 API/业务实现、单元和集成测试
ruoyi-framework/   安全认证、权限、数据源、Web 与框架配置
ruoyi-system/      系统用户、角色、字典、菜单及 Mapper
ruoyi-common/      通用模型、工具、注解和基础能力
assets/           安装所需公共图片及 SHA-256 清单
database/        完整结构、基础数据、商品配置、启动升级脚本
deploy/          初始化工具、配置模板、Nginx、systemd 与部署文档
pom.xml           父工程与依赖版本管理
```

## 验证与配置安全

```bash
mvn verify
python deploy/check_handoff.py
```

本地数据库集成测试需额外提供 `-Dwechat.test.properties=/absolute/path/test.properties`，仅允许 localhost/127.0.0.1 的独立测试库。无需数据库的单元测试默认执行，数据库/证书测试按条件启用。

环境配置、支付证书、上传资料、日志、数据库备份、`target` 均排除在 Git 外；只提交脱敏配置示例和空业务初始化数据。底层 RuoYi 许可保留于 [LICENSE](LICENSE)。

## 简历描述示例

参与商城 Java 后端开发，建设零售与分销双购买方式、库存预占、订单履约、微信支付退款及售后流程，并通过统一 REST API 支撑运营后台、小程序和 H5。完善重复通知处理、退款对账、权限管理及本地数据库回归验证。
