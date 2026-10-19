# 外部与内部接口电气与机械规范 (Interface Specification)

**文档编号**: ENG-SPEC-IF-001  
**版本**: v1.0.0  
**编制日期**: 2026-10-14  

---

## 1. 外部用户接口 (External User Interfaces)

### 1.1 USB Type-C 充电与应急数据接口 (J1)
- **物理形式**: 16-Pin / 6-Pin 沉板或贴片 USB Type-C 母座（如 KH-TYPE-C-16P-072B 或类似通用规格）。
- **引脚定义与电气特性**:
  - `VBUS (Pins A4, A9, B4, B9)`: 输入直流 $5.0\text{V} \pm 0.25\text{V}$，接入 500mA 自恢复保险丝与 TVS ESD 保护管。
  - `GND (Pins A1, A12, B1, B12)`: 系统电源地。
  - `CC1 (Pin A5)`: 外接 $5.1\text{ k}\Omega \pm 1\%$ 下拉电阻至地（使能 USB-C to USB-C 充电器 5V 输出握手）。
  - `CC2 (Pin B5)`: 外接 $5.1\text{ k}\Omega \pm 1\%$ 下拉电阻至地。
  - `D+ / D- (Pins A6, A7)`: 预留 ESD 保护，连入 MCU 原生 USB 数据脚或测试焊盘。

### 1.2 实体按键交互规格 (SW1, SW2, SW3)
- **SW1 (主翻页键 / NEXT)**: 
  - 贴片轻触微动开关 (6×6×5mm 或 3×6×2.5mm 侧按/正按按键，如 TS-1187A)。
  - 电气特性: 常开 (SPST-NO)，按压接地 (Active-Low)，外部并联 100nF 滤波电容与 10k 上拉电阻。
- **SW2 (副翻页键 / PREV)**: 
  - 同 SW1 规格，按压接地。
- **SW3 (功能/电源键 / PWR_PAIR)**: 
  - 超薄微动开关，支持长按开机、短按熄屏休眠、长按配对。

### 1.3 双色状态指示灯 (LED1, LED2)
- **LED1 (充电指示 - 红/绿双色 0603 共阳/共阴或双单色贴片)**:
  - 红色 (CHG_RED): 由 TP4057 `CHRG` 脚驱动，充电中恒流拉低导通常亮。
  - 绿色 (CHG_FULL): 由 TP4057 `STDBY` 脚驱动，充满电时拉低导通。
- **LED2 (系统与 BLE 状态 - 蓝色 0603)**:
  - 串联 $1\text{ k}\Omega$ 限流电阻由 MCU GPIO 高电平点亮，支持 PWM 呼吸灯。

---

## 2. 生产测试与自动化烧录接口 (Test & Flash Header / Pogo Pins)

为了满足批量生产快速自动化烧录与功能验收 (ATE)，PCB 底面设置标准金手指测试焊盘（间距 2.0mm 或 1.27mm，直径 1.0mm 裸铜沉金焊盘）：

| 测试点标号 | 信号名称 | 电气特性 | 功能描述 |
|:---|:---|:---|:---|
| **TP_VBUS** | VBUS_5V | 5V 输入 ($<500\text{ mA}$) | 工厂治具直接向板卡提供 5V 电源，检测充电回路 |
| **TP_3V3** | VCC_3V3 | 3.3V 输出/输入 ($\pm 3\%$) | 测量板载 LDO 输出电压及空载电流 |
| **TP_GND** | GND | 0V 参考地 | 治具参考地与回路 |
| **TP_TX** | UART0_TXD | 3.3V LVTTL | 接收固件输出日志与 ATE 测试响应 (115200 8N1) |
| **TP_RX** | UART0_RXD | 3.3V LVTTL | 发送 ATE 控制指令与自动化测试输入 |
| **TP_IO0** | BOOT0 / IO9 | 3.3V / 0V | 拉低后复位进入 ROM 引导烧录模式 (Download Mode) |
| **TP_EN** | CHIP_EN / RESET | 3.3V / 0V | 硬件复位引脚，拉低使芯片复位 |
| **TP_VBAT** | VBAT_TEST | 3.0V ~ 4.2V | 模拟锂电池供电与电量采样校准 |

---

## 3. 内部机电连接规范 (Internal Electro-Mechanical Interconnect)

### 3.1 锂电池连接
- **接口形式**: 焊盘直接焊接（预留 $\phi 1.5\text{ mm}$ 沉金通孔焊盘 `BAT+`, `BAT-`）或超小型 JST-ACH 1.2mm 2Pin 插座。
- **保护要求**: 电池本体自带精简 PCM 保护板（保护过放电压 $2.8\text{V}$，过充截止 $4.28\text{V}$）。

### 3.2 震动电机连接
- **型号规格**: 0827 扁平纽扣圆饼微动电机（直驱额定电压 3.0V，额定工作电流 45mA）。
- **安装位置**: 粘贴于下壳专用预留圆形沉槽内，背胶固定，引线焊接至 PCB 预留 `MTR+` 与 `MTR-` 焊盘。
