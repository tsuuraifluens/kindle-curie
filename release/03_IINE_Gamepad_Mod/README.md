# 方案三：良值迷你手柄固件修改方案 (IINE Gamepad Kindle Mod)

本方案针对市场上广受欢迎的**良值（IINE）口袋迷你蓝牙手柄（型号 L1161 / L1162，主控芯片珠海杰理 AC6321A / BD19 系列）**，通过受控二进制微代码补丁，将其改造为 100% 兼容 **Amazon Kindle Remote (Kindle Click)** 的精美实体无线翻页器。

**无需越狱 Kindle，无需拆开手柄，免焊接，使用官方 USB 升级程序直接下发刷机，且 100% 支持无损恢复原厂固件！**

---

## 固件版本选择 (Firmware Releases)

固件位于 `firmware/` 目录：

| 固件文件名 | 推荐等级 | 功能特性与实机状态 | SHA-256 校验和 |
| :--- | :---: | :--- | :--- |
| **`IINE_Kindle_Click_v25_SleepLockout.ufw`** | ★★★★★<br>**(★当前最新推荐)** | **原生按键状态锁存防误触**：在 v24 极速连接基础上，利用系统内置按键状态锁存机制，**彻底消除双手松开 A+B 时的微观异步抬手导致的误翻页与睡眠打断！** 零 RAM 写入、零 RTOS 冲突，单按 A/B 键 0ms 零延迟响应。包含秒连与全套功能。 | `56D4DE1DBF5B98222310CDEDFCBEEC85EE7B2E1D97F1AB05A9DB9BC071F1DF23` |
| **`IINE_Kindle_Click_v24_FastConnect.ufw`** | ★★★★★<br>*(极速秒连版)* | **开机秒连优化**：将 BLE 广播间隔优化为 **20ms** 极限密度并直连 `ADV_IND`，回连仅需 1~3 秒。含 A+B 熄屏（无抬手延迟屏蔽）。 | `5E7448EF3AFB53671FF4E2A56743659FED9A916FF5F937E2FBE0F235690F2072` |
| **`IINE_Kindle_Click_v23_ABchord.ufw`** | ★★★★☆<br>*(组合键版)* | 在 v22s 稳定版基础上，追加实现 **A+B 同时按下熄屏/睡眠**（注入 Consumer Power `0x0030`）。 | `F4B18CD49CF547CADBE465F2C0F1937495761FF72A4D6F84E2E595CE280A6879` |
| **`IINE_Kindle_Click_v22s_keymap.ufw`** | ★★★★☆<br>*(备选稳定版)* | 基础稳定版：针对单手阅读习惯深度优化，A/B 翻页、方向键翻页与左右亮度调节完全正常（无 A+B 熄屏）。 | `2E517183D309BB2754DD30C6B056F0F6FC05A83335AADB2D82AE88C5E7DDB5FC` |
| **`IINE_Kindle_Click_v21b_Composite.ufw`** | ★★★☆☆<br>*(基准备用)* | 早期全功能实机通过版本（保留手柄出厂旧键位）。 | `29FDADE0BE86BA0AFD27569970D70620EBCBD8A7CE9DB54E920EEDBF9945729C` |

---

## 按键功能映射表 (Keymap Reference)

### 推荐稳定版 (`v22s`) 键位：
```
              ┌─────────────────────┐
              │      [ 配对键 ]     │
              │         ▲ (下一页)   │
   (亮度 -) ◀ ┼ ▶ (亮度 +)          (A) 上一页
              │         ▼ (上一页)   │
              │                     (B) 下一页
              └─────────────────────┘
```

| 物理按键 | 映射功能 | 底层 HID 报文与协议依据 | 对应 Kindle 系统响应 |
| :--- | :--- | :--- | :--- |
| **按键 A** | **上一页 (Prev)** | Keyboard Usage `0x4B` (`KEY_PAGEUP`) | 电子书翻回上一页 |
| **按键 B** | **下一页 (Next)** | Keyboard Usage `0x4E` (`KEY_PAGEDOWN`) | 电子书翻到下一页 |
| **方向键 ↑ (上)** | **下一页 (Next)** | Keyboard Usage `0x4E` (`KEY_PAGEDOWN`) | 满足部分读者拇指习惯 |
| **方向键 ↓ (下)** | **上一页 (Prev)** | Keyboard Usage `0x4B` (`KEY_PAGEUP`) | 满足部分读者拇指习惯 |
| **方向键 ← (左)** | **降低亮度** | Consumer Control Usage `0x0070` (`Brightness Down`) | Kindle 屏幕前光减弱 1 级 |
| **方向键 → (右)** | **提高亮度** | Consumer Control Usage `0x006F` (`Brightness Up`) | Kindle 屏幕前光增强 1 级 |
| **A + B 键 (v23/v24/v25)** | **熄屏 / 睡眠 (带防误触屏蔽)** | Consumer Control Usage `0x0030` (`Power/Sleep`) | Kindle 立即熄屏休眠 (v25 附带 2.5s 抬手防误触屏蔽) |

---

## 极速刷机指南 (Flash Guide)

> [!TIP]
> 刷机工具位于 `flasher/` 目录，请在 Windows 10/11 电脑上操作。全程耗时约 10 秒。

1. **打开升级程序**:
   - 进入 `flasher/` 目录，双击打开 `L1161 固件升级程序_1.4.40.exe`。
2. **选择修改版固件**:
   - 点击界面左上方的「选择文件」，在 `firmware/` 目录中选中推荐的 `IINE_Kindle_Click_v22s_keymap.ufw`。
3. **确认勾选项**:
   - 勾选 **「擦除整个 FLASH」**。
   - 勾选 **「成功后复位」**。
4. **手柄进入升级模式**:
   - 将手柄开机，用 Type-C 数据线连上电脑。
   - **同时长按手柄「配对键 + B 键」约 2 秒**。
   - 手柄指示灯熄灭，电脑发出 USB 插入提示音，升级工具底部显示 **ONLINE**。
5. **一键升级**:
   - 点击工具上的 **「升级」** 按钮。
   - 进度条满格后提示 **「升级成功」**，手柄自动重启并启动。
6. **Kindle 蓝牙连接**:
   - 在 Kindle 上进入 **全部设置 -> 蓝牙与无线 -> 蓝牙**。
   - 若之前配对过手柄，先点击并选择「**忽略该设备**」。
   - 点击「**配对新设备**」，选中扫描出的 `"Kindle Remote"` 完成配对！

---

## 100% 救砖与恢复原厂说明 (Unbricking Guarantee)

由于杰理 AC632N 芯片内置只读、物理不可损毁的 **硬件 Mask ROM**，升级工具原生支持芯片底层下载协议：
- 任何时候如需恢复出厂，或遇到任何异常，只需重新按住「**配对键 + B 键**」插线进入升级模式。
- 选择 `flasher/stock_backup/L1161 L1162.ufw` 原厂固件包刷入，即可 100% 完整复原，绝无变砖风险！
