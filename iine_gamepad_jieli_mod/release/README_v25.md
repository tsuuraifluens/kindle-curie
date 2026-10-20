# 良值 (IINE) 自拍手柄 Kindle 翻页固件 - v25 熄屏防误触版

## 版本特性

1. **A+B 熄屏后按键状态锁存防误触机制**：
   - 彻底解决双手抬起微动时间差（异步抬手）导致的误发下一页（B 键残留）或上一页（A 键残留）问题。
   - 读取原厂固件内置的 `prev_key`（`0x2B40`）按键状态，当 A+B 触发 Consumer Power（`0x0030`）后，在两指未完全松开的微观时间窗口内持续锁存为 Power 键，固件检测到状态未变则绝不发出新的按键报文，有效屏蔽异步抬手残存信号。
   - 双键完全脱离行程后瞬间释放，单键翻页（A 翻前页 / B 翻后页）维持 0ms 硬件零延迟，不占用任何 RAM，无 RTOS 冲突风险。
2. **继承 v24 极速连接特性**：
   - 广播间隔优化为 BLE 规范极限 20ms（32 slots），回连 Kindle 只需 1~3 秒。
   - 自动绕过无 payload 的定向广播，确保 Kindle 始终命中白名单广播包。
3. **继承 v23 完整键位与描述符**：
   - A 键：上一页（`PageUp`）
   - B 键：下一页（`PageDown`）
   - A+B 同时按住：Kindle 熄屏 / 唤醒（Consumer Power `0x0030`）
   - 方向键上：下一页（`PageDown`）
   - 方向键下：上一页（`PageUp`）
   - 方向键左：Kindle 亮度减（Consumer `0x0070`）
   - 方向键右：Kindle 亮度加（Consumer `0x006F`）
   - Amazon Lab126 VID `0x0171` / PID `0x0443` 欺骗

---

## 固件哈希核对

- **文件**: `IINE_Kindle_Click_v25_SleepLockout.ufw`
- **大小**: 645,184 字节
- **SHA-256**: `56D4DE1DBF5B98222310CDEDFCBEEC85EE7B2E1D97F1AB05A9DB9BC071F1DF23`

---

## 刷机指南

1. 打开 [flasher/L1161 固件升级程序_1.4.40.exe](file:///c:/Users/weish/Documents/antigravity/kind-curie/release/03_IINE_Gamepad_Mod/flasher/L1161%20%E5%9B%BA%E4%BB%B6%E5%8D%87%E7%BA%A7%E7%A8%8B_1.4.40.exe)。
2. 加载固件 `IINE_Kindle_Click_v25_SleepLockout.ufw`。
3. 勾选 **“擦除整个FLASH”** 与 **“成功后复位”**。
4. 手柄关机状态下，按住 **`配对键 + B键`** 插入 USB 数据线连接电脑。
5. 识别到设备后点击 **“开始下载”**（3~5 秒即可完成烧录）。
