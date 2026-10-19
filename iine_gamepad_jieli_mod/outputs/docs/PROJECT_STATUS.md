# 项目总体状态

更新时间: 2026-10-19 (第三次更新)

## 当前阶段
- 阶段 0-3: **完成**
- 阶段 1 (芯片识别): **完成** — AC6321A 已由丝印确认
- 阶段 2 (路线): **完成** — 路线 B
- 阶段 4 (按键/蓝牙逆向): **部分** — 配置已解码; 按键 GPIO 待万用表实测
- 阶段 5 (固件构建): **准备中** — SDK/工具就绪, 待 pi32 工具链 + GPIO + flash 确认
- 阶段 6/7/8: 未开始/部分

## 解扰逆向 (已完成)
1. **扰码算法完全破解**: CRC16-CCITT 流（poly 0x1021, init 0xFFFF, 先输出低字节），32 字节块。
2. 解出顶层头部: `AC632N`、镜像长度 `0x9D840`。
3. 解出文件表: uboot.boot@0x4A0/4831B、isd_config.ini@0x177F/122B、app_dir_head@0x2400、key_mac@0x7F400/4096B。
4. **uboot 与 SDK 原版逐字节一致**（XOR 验证），可直接复用 SDK 的 uboot.boot。
5. 详见 `docs/DESCRAMBLE_FINDINGS.md`。

## 本轮新增
1. 芯片精确型号确认: **AC6321A** (丝印 BP3F723-21A4; QFN32; 4Mbit flash 高置信)。
2. .ufw 容器 trailer 格式解码: `[CRC][0x42733665][0x00*8]["JLUFW"][pad]`。
3. SDK 参考 .fw/.ufw 结构对照完成 (bd19 default_cfg.fw / bd29 update.ufw)。
4. GPIO 测量指引已产出 (docs/GPIO_MEASUREMENT_GUIDE.md)。

## 阻塞项
- 按键 GPIO 映射 (需万用表实测)。
- Flash 容量最终确认 (512KB vs 1MB)。
- pi32 工具链未安装。
- 原厂恢复路径未实测。

## 下一步
1. 用户拆机测按键 GPIO (或提供主板高清照片)。
2. 下载 pi32 工具链。
3. 搭建 src/firmware 工程并实现 Kindle Click 协议。
4. 离线构建 + 静态校验。
5. 刷写前: 备份 + 恢复实测 + 检查表。
