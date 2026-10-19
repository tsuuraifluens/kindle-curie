# 路线 B 实施计划: 基于 fw-AC63_BT_SDK 重建 Kindle Click 固件

## 已确定硬件
- 芯片: **AC6321A** (QFN32, pi32, 73KB RAM) — 丝印 BP3F723-21A4
- Flash: 4Mbit(512KB) 高置信 (丝印 `4`), 待实机最终确认
- SDK 板级配置: `apps/hid/board/bd19/board_ac6321a_demo_cfg.h` (CONFIG_CHIP_NAME=AC632N, 默认 FLASH_SIZE_1M — 需按实际 flash 改)
- 原厂配置: SPI 2_3_0_0; 复位键 PB01(8s); 串口升级 RX=PB00

## 已就绪条件
- SDK 本地副本: analysis/reference/fw-AC63_BT_SDK (commit fdc018de81629e21e594d677cf05f87d288be0f0)
- 工具链(文件): cpu/bd19/tools/ 下 isd_download.exe / ufw_maker.exe / fw_add.exe / lz4_packet.exe / uboot.boot / ota.bin / bd19loader.bin / p11_code.bin / flash_params.bin
- HID 能力: apps/hid/modules/bt/ble_hogp.c (HOGP) + le_hogp_set_ReportMap + 0xFF 厂商数据广播 API
- 参考实现: apps/hid/examples/keyboard / keypage(翻页器) / standard_keyboard
- 协议规格: analysis/protocol/kindle-click-spec.md

## 缺口 (构建前必须)
1. **pi32 工具链** (C:\JL\pi32) — 杰理官网下载
2. **Flash 容量实机确认** (512KB vs 1MB) — 看 flash 芯片丝印 或 万用表/原理图
3. **按键 GPIO 映射** — 万用表蜂鸣档实测 (docs/GPIO_MEASUREMENT_GUIDE.md)
4. 电池分压通路 (电量服务, 可选)
5. 原厂恢复路径实测 (刷写前必须)

## 实现步骤
1. 复制 apps/hid → src/firmware, 选择 AC6321A 板级配置。
2. 蓝牙身份: ADV_IND Flags 0x06 + 厂商数据 `71 01 04 43`(唯一 0xFF AD); DIS PnP ID `01 71 01 43 04 00 01`; 禁广播 0xFE03。
3. HID: 自定义 10 字节 Report Map (键盘+consumer, ID 1)。
4. 按键: GPIO 扫描 + 去抖 + 成对按下/释放; 侧键长按 1s→单次 0x0030; 翻页键断连排队补发; 20ms 广播重连。
5. 电池: 实测分压换算, BAS 通知 (连接后 + 周期)。
6. 构建: download.bat → app.bin → isd_download -output(离线) → fw_add → ufw_maker → update.ufw。
7. 静态校验 + (可选) BLE 抓包 + Kindle 实机清单。

## 安全红线
- 候选固件离线生成; 不接设备不自动烧录。
- 首次烧录前: 原厂备份在工作区外 + 恢复路径实测 + 检查表通过。
- 失败即停, 回退原厂 .ufw。