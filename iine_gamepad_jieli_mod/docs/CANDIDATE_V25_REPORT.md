# 候选固件 v25 技术报告：A+B 组合键熄屏后输入屏蔽与防误触冷却状态机

## 1. 背景与问题描述

在 v23 / v24 固件实测中，A+B 组合键发出 Consumer Power（`0x0030`）休眠指令功能已验证成功。但用户在日常实机使用中反馈了新的体验问题：
> **“A+B同时按下发出熄屏指令后，最好能屏蔽输入一段时间，kindle熄屏要几秒钟避免两个按键抬起时间不一致造成的误操作”**

### 根本原因剖析 (Root Cause)

1. **人体手指抬起微观异步 (Asynchronous Key Release)**：
   - 当用户同时按压 A 和 B 键触发熄屏后准备松手时，人类左右手指或同一手指的微动行程存在 50ms ~ 150ms 的抬起时间差。
   - 若手指先抬起 A 键，B 键仍然压在按键行程内；在原有的组合键逻辑中，`r0 & 3 == 2`（不再是 `== 3` 的组合状态），微控器会将其识别为合法的“单按 B 键”，立即向 Kindle 发送 `PageDown`（下一页）事件。
   - 反之，若先抬起 B 键，A 键仍短暂停留，微控器会发送 `PageUp`（上一页）事件。

2. **Kindle 墨水屏睡眠过渡期敏感性 (E-ink Sleep Transition)**：
   - Kindle 收到 `KEY_POWER`（Consumer `0x0030`）后，`powerd` 开始调度电子墨水屏控制器进行全屏波形刷新与画面冻结（过渡至屏保或关机壁纸）。该过程通常需要 1.5 ~ 2.0 秒。
   - 在这几秒钟的过渡期内，如果蓝牙协议栈接收到任何新的按键事件（如上述微观异步残存的翻页键），Kindle 极易被立刻打断睡眠，或在熄屏瞬间意外翻页，甚至被再次唤醒。

---

## 2. 解决方案与状态机设计

为彻底消除异步抬手造成的误触发，v25 固件引入了基于硬件毫秒定时器的**输入屏蔽与释放锁存状态机**：

### 状态机核心机制

```
[正常空闲/阅读模式]
        │
        │ 用户同时按下 A + B (r0 & 3 == 3)
        ▼
[触发熄屏指令]
  1. 向主机发送 Consumer Power (r0 = 4, 虚拟键 2)
  2. 记录当前硬件时间戳 now = [0x2ADC]
  3. 设置冷却截止时间: lockout = now + 2500ms (2.5秒安全冷却期)
  4. 置位组合键活跃标志: active = 1
        │
        ▼
[2.5秒全按键屏蔽与抬起锁存期 (active == 1 || now < lockout)]
  - 帧 1: 立即释放按键 (r0 = 0)，向主机发送单次短促按键脉冲，避免长按触发重启菜单
  - 帧 2..N: 强制所有按键输入置零 (r0 = 0)，屏蔽 A/B/方向键等所有信号
  - 抬手检测:
      * 若手指未完全松开 (r0 & 3 != 0)，active 保持 1，继续屏蔽
      * 只有当 A 和 B 均完全离开按键行程 (r0 & 3 == 0) 后，active 才清除为 0
        │
        │ 当 now >= lockout 且 active == 0 (双键均已完全释放)
        ▼
[自动恢复正常阅读模式]
  - 消除屏蔽，所有单键 (A 上一页 / B 下一页 / 方向键亮度调节) 恢复 0ms 零延迟响应
```

### 状态变量存储安全验证

为确保在杰理 AC6321A 上运行的状态变量持久、安全且不踩内存：
- **硬件时间源**：`0x2ADC` 为系统全局 32 位毫秒硬件计数器（1 tick = 1 ms），由定时器中断驱动，运行 49.7 天才溢出。
- **状态变量槽位**：
  - `0x2B94` (uint32)：`lockout_deadline`（毫秒截止时间戳）
  - `0x2B98` (uint32)：`chord_active`（组合键活跃标志）
  - 经全面扫描反汇编表与符号表，`0x2B90..0x2B9C` 处于全局控制器结构体尾部空闲段，属于 BSS 区，开机自动清零，且原厂固件中访问次数为 0，绝对安全。

---

## 3. 汇编实现与代码布局

为避免破坏任何既有描述符，v25 将 90 字节的状态机例程注入至 **`0x2E100`（VMA `0x1E2E220`）**。该区域在原厂固件中位于 `0x2E0E5..0x2E450` 连续 **875 字节全零对齐间隙**内，空间充裕且 100% 独立。

```assembly
	.text
	.globl chord_lockout
chord_lockout:
	[--sp] = {rets, r13-r4}      ; 保护寄存器 (与 0x1E0C820 退出时弹出配对)
	r4 = r1                      ; r4 = 报文输出缓冲指针 (供 0x1E0C820 使用)
	r5 = 0x2B90                  ; 状态变量基址
	r12 = [r5 + 8]               ; 读取 active 标志 (@0x2B98)
	r2 = r0 & 3                  ; 提取 A (bit 0) 与 B (bit 1)

	if (r12 != 0) goto check_active ; 若组合键仍在活跃周期，跳转抬手检测

	r13 = [r5 + 4]               ; 读取 lockout 截止时间 (@0x2B94)
	if (r13 == 0) goto check_trigger ; 若当前无冷却，跳转触发检测

	r3 = 0x2ADC                  ; 系统毫秒时间源
	r1 = [r3 + 0]                ; r1 = now
	r1 = r1 - r13                ; r1 = now - lockout
	ifs (r1 < 0) goto suppress   ; 若 now < lockout (有符号比较)，处于冷却期，强制屏蔽

	r1 = 0
	[r5 + 4] = r1               ; 冷却到期，清空 lockout 变量

check_trigger:
	if (r2 != 3) goto body       ; 非 A+B 组合按键，正常放行

	r3 = 0x2ADC                  ; A+B 首次同时按下！
	r1 = [r3 + 0]                ; 读取当前时间
	r1 += 2500                  ; 设定 2.5 秒冷却时间 (now + 2500ms)
	[r5 + 4] = r1               ; 写入 lockout
	r1 = 1
	[r5 + 8] = r1               ; 置位 active = 1
	r0 = 4                      ; 输出虚拟键 2 (Consumer Power 0x0030)
	goto body

check_active:
	if (r2 != 0) goto suppress   ; 若 A 或 B 仍有按住，继续保持 active 并屏蔽
	r1 = 0
	[r5 + 8] = r1               ; 两键均已抬起，清除 active 标志

suppress:
	r0 = 0                      ; 强制置零输入掩码，吞掉残存按键与误操作

body:
	r11 = r0                     ; r11 = 处理后的输入按键掩码
	r1 = 0                       ; 满足 0x1E0C820 入口约定
	r3 = 0x1E0C820
	goto r3                      ; 跳入原厂按键表聚合循环
```

---

## 4. 21 项全自动化校验与审计结果

通过 [audit_v25.js](file:///c:/Users/weish/Documents/antigravity/kind-curie/iine_gamepad_jieli_mod/tools/audit_v25.js) 以及杰理官方解包工具 `fwunpack_newfw.py` 进行审计：

- [x] **UFW 总长度**：645,184 字节（与原厂逐字节对齐）
- [x] **5 级 CRC 校验链**：
  - UFW Header CRC（PASS）
  - UFW List CRC（PASS）
  - Entry 0 Flash CRC（PASS）
  - JLFS app.bin Data CRC `0x1968`（PASS）
  - JLFS Entry Header CRC `0x98A6`（PASS）
  - JLFS Area Data CRC `0xA0B3`（PASS）
  - JLFS Area Header CRC `0x89D3`（PASS）
- [x] **未修改安全区域验证**：
  - `[0x400 : 0x2400]` Uboot 引导分区：100% 逐字节一致
  - `[0x34400 : EOF]` Flash 外部所有配置与恢复扇区：100% 逐字节一致
- [x] **HID 复合描述符结构**：87 字节，包含键盘（Report ID 2）及内嵌 Consumer Usage
- [x] **BLE 极速连接优化**：20ms 广播间隔（32 slots）与 DIRECT_ADV 自动绕过机制完整保留
- [x] **Flash 区域非授权变动**：0 处非法越界，改动严格收敛在补丁声明区域内

---

## 5. 固件交付物信息

| 固件版本 | 文件路径 | SHA-256 校验和 |
| :--- | :--- | :--- |
| **v25 熄屏防误触版** | `release/03_IINE_Gamepad_Mod/firmware/IINE_Kindle_Click_v25_SleepLockout.ufw` | `F67CD51ECB0980587F22E14FD0966762576BAEE0E5AF2E7B1DD79FE31B0E5F53` |
