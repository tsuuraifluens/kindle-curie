# 硬件原理图设计说明与计算选型报告 (Schematic Design & Engineering Calculations)

**文档编号**: ENG-HW-SCH-001  
**项目名称**: Kindle Remote 工业级实体翻页器硬件设计  
**PCB 规格**: 50.0 mm × 22.0 mm, 2 层 FR-4, 1.2 mm 厚度  
**主控**: Espressif ESP32-C3-WROOM-02-N4 (内置 4MB Flash)  
**日期**: 2026-10-14  

---

## 1. 核心电路计算与元器件选型论证

### 1.1 锂电池充电管理电路 (TP4057)
- **选用芯片**: 南京拓微 TP4057 (SOT-23-6 封装)。
- **核心优势**: 采用双灯独立状态指示，具备反接保护与热过载保护，体积仅 $2.9\text{ mm} \times 1.6\text{ mm}$，静态漏电 $< 2\ \mu\text{A}$。
- **充电电流设定计算**:
  充电电流公式:
  $$I_{BAT} = \frac{1000}{R_{PROG}}$$
  对于标称容量为 $200\text{ mAh}$ 的 402030 聚合物锂电池，推荐标准充电倍率为 $0.5\text{C}$，即目标充电电流为 $100\text{ mA}$：
  $$R_{PROG} = \frac{1000}{0.100\text{ A}} = 10,000\ \Omega = 10\text{ k}\Omega$$
  选型采用 $10\text{ k}\Omega \pm 1\%$ 0603 高精度贴片精密电阻。
- **输入防倒灌与去耦**:
  VCC 输入端并联 $10\ \mu\text{F}$ 0805 X5R 陶瓷电容，BAT 输出端并联 $10\ \mu\text{F}$ 0805 陶瓷电容以抑制纹波并稳定环路。

### 1.2 稳压供电与低压差电源树 (ME6211C33)
- **选用芯片**: 微盟 ME6211C33M5G (SOT-23-5 封装)。
- **输出规格**: 标称输出 $3.3\text{V} \pm 1\%$，最大持续输出能力 $500\text{ mA}$，完全满足 ESP32-C3 瞬态射频发射峰值电流 ($< 280\text{ mA}$)。
- **压差与静态功耗计算**:
  - 满载 $100\text{ mA}$ 时压差仅为 $100\text{ mV}$。锂电池放电截止电压为 $3.4\text{V}$ 时，稳压器依然能稳定输出 $3.30\text{V}$。
  - 典型静态电流 $I_Q = 30\ \mu\text{A}$。
- **去耦滤波网络**:
  输入端配置 $10\ \mu\text{F}$ 陶瓷电容，输出端配置 $10\ \mu\text{F} + 100\text{ nF}$ 组合并联，有效滤除高频射频开关噪声。

### 1.3 零静态漏电电量分压采样电路 (Battery Voltage Sensing)
- **传统分压电阻缺陷**:
  若直接采用常态分压电阻（如两个 100kΩ）跨接在锂电池两端，将产生恒定的：
  $$I_{leak} = \frac{4.2\text{V}}{200\text{ k}\Omega} = 21\ \mu\text{A}$$
  这比 MCU 深度休眠总电流还要大，是极其致命的持续电量耗损。
- **开关控制方案设计**:
  采用 P-Channel MOSFET (AO3401A, $R_{DS(on)} < 50\text{ m}\Omega$) 作为上桥臂电子开关：
  - 源极 (S) 接锂电池 $V_{BAT}$。
  - 栅极 (G) 通过 $100\text{ k}\Omega$ 上拉电阻接到 $V_{BAT}$，确保常态可靠截止。
  - 漏极 (D) 接入两只 $100\text{ k}\Omega \pm 1\%$ 0603 高精度精密分压电阻 $R_1, R_2$。
  - MCU 的 `IO4 (VBAT_SENSE_EN)` 控制栅极拉低。在需要采样前，IO4 输出低电平导通 AO3401A；ADC 采样完成后，IO4 恢复高阻/高电平，**分压网络回路静态电流彻底为 0**！
- **ADC 分压量程匹配**:
  $$V_{ADC} = V_{BAT} \times \frac{R_2}{R_1 + R_2} = V_{BAT} \times 0.5$$
  当锂电池处于满电 $4.20\text{V}$ 时，$V_{ADC} = 2.10\text{V}$；
  当锂电池处于欠电 $3.30\text{V}$ 时，$V_{ADC} = 1.65\text{V}$。
  完全落入 ESP32-C3 ADC1（内部衰减设置 `ADC_ATTEN_DB_11`，参考测量范围 0 ~ 2.5V）的最佳线性区间内。并在中点对地并联 $10\text{ nF}$ 贴片电容滤除瞬态采样噪声。

### 1.4 震动电机驱动与保护电路 (Haptic Motor Driver)
- **选用器件**: 0827 微型扁平纽扣圆饼振动电机（工作电压 2.7~3.3V，额定电流 45mA）。
- **开关功率管**: N-Channel MOSFET (AO3400A, SOT-23, $V_{DS} = 30\text{V}, I_D = 5.7\text{A}, R_{DS(on)} \approx 28\text{ m}\Omega$)。
- **线圈续流保护**:
  电机内部线圈属于感性负载，在 MOSFET 关断瞬间会产生高达数十伏的反向电动势 $\left(V = -L \frac{di}{dt}\right)$。因此在电机两端反向并联一只 1N5819WS 肖特基二极管（耐压 40V，压降 0.35V），将反峰钳位在安全范围内，彻底消除击穿 MOS 管与干扰主控电源轨的隐患。
- **阻尼下拉**:
  MOSFET 栅极对地加装 $10\text{ k}\Omega$ 下拉电阻，栅极输入端串联 $100\ \Omega$ 限流电阻，防止上电复位期间浮空产生意外微震。

### 1.5 USB Type-C 保护与数据网络 (USB Protection)
- **CC 逻辑配置**:
  $A_5$ (CC1) 与 $B_5$ (CC2) 引脚分别独立串联一只 $5.1\text{ k}\Omega \pm 1\%$ 贴片电阻下拉至 GND，严格遵循 USB Type-C 规范，保证支持任何 USB-C 充电头（含 PD/QC 协议适配器）自动输出 5V。
- **ESD 保护网络**:
  D+ 与 D- 走线并联 USBLC6-2SC6 (SOT-23-6) 微型超低结电容 ($< 3.5\text{ pF}$) TVS 阵列，提供高达 $\pm 15\text{ kV}$ 气隙放电保护。
- **输入限流保险**:
  VBUS 串联 0805 封装 500mA 自恢复保险丝 (PTC / PPTC)。

---

## 2. GPIO 资源映射总表 (Complete Pin Assignment)

| ESP32-C3 引脚 | 模块管脚名称 | 连接网络标号 (Net Name) | 电气属性 / 外部电路 | 固件功能与状态机定义 |
|:---:|:---:|:---:|:---:|:---|
| **Pin 1** | 3V3 | `+3V3` | 3.3V 电源输入 (并联去耦电容) | 系统主供电电源轨 |
| **Pin 2** | EN | `CHIP_EN` | 10k 上拉到 3V3 + 1uF 到地 + TP_EN | 硬件复位与延时启动 |
| **Pin 3** | GPIO4 | `VBAT_SENSE_EN` | 接 P-MOS 栅极控制驱动 | 电池分压采样低电平使能开关 |
| **Pin 4** | GPIO5 | `SW_PWR` | 外部 10k 上拉 + 按键对地 (SW3) | 电源/休眠/配对微动开关 (RTC 唤醒源) |
| **Pin 5** | GPIO6 | `HAPTIC_DRV` | 100Ω 串联至 N-MOS 栅极 | 震动电机 18ms 脉冲驱动输出 |
| **Pin 6** | GPIO7 | `LED_BLE` | 串联 1kΩ 限流电阻至蓝色 LED | BLE 状态与事件指示输出 |
| **Pin 7** | GPIO8 | `GPIO8` | 模组内部直连 | Strapping 引脚 (默认上拉) |
| **Pin 8** | GPIO9 | `BOOT0` | 10k 上拉到 3V3 + TP_IO0 测试焊盘 | 启动配置 Strapping (拉低进入下载模式) |
| **Pin 9** | GND | `GND` | 主地回路与热沉焊盘 | 系统参考地 |
| **Pin 10**| GPIO10| `NC` | 悬空不布线 | 备用扩展引脚 |
| **Pin 11**| RXD / IO20 | `UART0_RXD` | 接测试焊盘 TP_RX | 生产 ATE 调试与固件串口接收 |
| **Pin 12**| TXD / IO21 | `UART0_TXD` | 接测试焊盘 TP_TX | 生产 ATE 调试与固件串口发送 |
| **Pin 13**| GPIO18| `USB_DM` | 串联 22Ω 阻尼电阻连至 Type-C A7/B7 | 原生全速 USB D- 数据线 |
| **Pin 14**| GPIO19| `USB_DP` | 串联 22Ω 阻尼电阻连至 Type-C A6/B6 | 原生全速 USB D+ 数据线 |
| **Pin 15**| GPIO3 | `VBAT_ADC` | 接分压电阻中点 (并联 10nF 电容) | ADC1_CH3 锂电电压采样输入 |
| **Pin 16**| GPIO2 | `GPIO2` | 模组内部直连 | Strapping 引脚 (内部上拉) |
| **Pin 17**| GPIO1 | `SW_NEXT` | 外部 10k 上拉 + 按键对地 (SW1) | 主翻页按键 (下一页 / PageDown) |
| **Pin 18**| GPIO0 | `SW_PREV` | 外部 10k 上拉 + 按键对地 (SW2) | 副翻页按键 (上一页 / PageUp) |
