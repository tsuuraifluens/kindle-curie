# 良值 (IINE) 自拍手柄 Kindle 翻页固件 - v25 熄屏防误触版

## 版本特性

1. **A+B 熄屏后 2.5 秒全按键输入屏蔽与防误触状态机**：
   - 彻底解决双手抬起微动时间差（异步抬手）导致的误发下一页（B 键残留）或上一页（A 键残留）问题。
   - 触发 A+B 熄屏指令后，立即发出标准单击 Consumer Power（`0x0030`）脉冲，随后启动 2.5 秒硬件定时器冷却屏蔽。
   - 在 Kindle 墨水屏全屏波形刷新与休眠过渡的 2.5 秒内，强制屏蔽所有按键输入，保证 Kindle 睡眠过程绝对不被打断。
   - 具备按键完全抬起锁存检测（Release Latch）：即使长时间按住 A+B 不放，也绝不重复发送电源键或弹出重启菜单，两键完全松开后自动恢复待命。
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
- **SHA-256**: `F67CD51ECB0980587F22E14FD0966762576BAEE0E5AF2E7B1DD79FE31B0E5F53`

---

## 刷机指南

1. 打开 [flasher/L1161 固件升级程序_1.4.40.exe](file:///c:/Users/weish/Documents/antigravity/kind-curie/release/03_IINE_Gamepad_Mod/flasher/L1161%20%E5%9B%BA%E4%BB%B6%E5%8D%87%E7%BA%A7%E7%A8%8B_1.4.40.exe)。
2. 加载固件 `IINE_Kindle_Click_v25_SleepLockout.ufw`。
3. 勾选 **“擦除整个FLASH”** 与 **“成功后复位”**。
4. 手柄关机状态下，按住 **`配对键 + B键`** 插入 USB 数据线连接电脑。
5. 识别到设备后点击 **“开始下载”**（3~5 秒即可完成烧录）。
