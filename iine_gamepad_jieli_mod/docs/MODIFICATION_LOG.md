# 修改记录 (Modification Log)

所有修改只发生在派生副本。原厂 `analysis/firmware/L1161 L1162.ufw`
保持只读基线，SHA-256
`73005E7C885AAB2A644ADFDF2DBADC01802B34427E9E6FEBF823F50D4D7745E9`。

## v18 (已实机, 失败)
目标: 通过 PnP 构造函数常量改 VID/PID。
结果: Kindle 能连接，但按键全无效。原因: 构造函数有前置早退条件。

## v19 (已实机, 部分成功)
目标: 在 PnP 读取分支无条件直写 Amazon VID/PID。

| app.bin 偏移 | 原字节 | 新字节 | 说明 |
|---|---|---|---|
| 0x44AE | `00 fb 53 43` | `00 fb 71 01` | r0 = 0x0171 |
| 0x44C0 | `00 fc b6 57` | `00 fb 43 04` | r0 = 0x0443 |
| 0x44CE | `41 16` | `00 00` | NOP 掉 helper 调用 |

同时保留 v1.7 的 GAP/DIS/ADV/模式/键表补丁。
结果: 翻页正常; 亮度无效。

## v20 (已构建, 待实机)
目标: 亮度改走 Consumer page。

| app.bin 偏移 | v19 | v20 | 说明 |
|---|---|---|---|
| 0x22908 | `02 00 00 70 00 00` | `04 00 70 00 00 00` | 左: Consumer 0x0070 |
| 0x2290E | `02 00 00 6f 00 00` | `04 00 6f 00 00 00` | 右: Consumer 0x006F |

产物: `outputs/IINE_Kindle_Click_v20_Consumer.ufw`
SHA-256: `CCAE44F3DFC0D310C61F51A84B93D19D47BC9CAD6C9EF4294A2F504E12F7A519`

审计: `tools/audit_v20.js` PASS。解密后 app 区只差两条键表 + JLFS
CRC; app 区外原始字节完全一致。

实机结果: 翻页正常，亮度仍无效。独立 Consumer Report ID 5 未被
Kindle 接受。

## v21 (已构建, 待实机)
目标: 把 Consumer usage 并入键盘 Report ID 2 / 同一 collection。

| app.bin 偏移 | 原值 | 新值 | 说明 |
|---|---|---|---|
| 0xABBE | `50 74` | `70 75` | 键盘描述符长度 69 -> 87 |
| 0xABC2 | `c1 ff 2a 27 e2 01` | `c1 ff 60 1a e3 01` | 指针 -> 0x1E31A60 |
| 0xC7C6 | `e8 58` | `38 59` | Consumer usage1 -> 聚合 +38 |
| 0x31940 | 00... | 87B | 新复合描述符 |

键表保留 v20 的 type 4 `04 00 70/6f 00 00 00`。

产物: `outputs/IINE_Kindle_Click_v21_Composite.ufw`
SHA-256: `F8FBAC88DFB645DB243A733402B44B55DB356508D822A5612CC2BD75C499A5BD`

审计: `tools/audit_v21.js` PASS，含 HID 描述符解析:
Report ID 2、Input 8 字节、Output 1 字节、包含 Consumer page。

实机/工具结果: 官方升级程序**无法识别** v21。最可能原因是描述符
放在 app.bin 之外的尾部空白，触发了文件级校验。

## v21b (已构建, 待实机)
与 v21 相同的复合描述符，但移到 app.bin 内部的全 0 对齐空隙。

| app.bin 偏移 | 原值 | 新值 | 说明 |
|---|---|---|---|
| 0xABBE | `50 74` | `70 75` | 长度 69 -> 87 |
| 0xABC2 | `c1 ff 2a 27 e2 01` | `c1 ff 50 34 e2 01` | 指针 -> 0x1E23450 |
| 0xC7C6 | `e8 58` | `38 59` | Consumer usage1 -> +38 |
| 0x23330 | 00... | 87B | 复合描述符 (app.bin 内) |

产物: `outputs/IINE_Kindle_Click_v21b_Composite.ufw`
SHA-256: `29FDADE0BE86BA0AFD27569970D70620EBCBD8A7CE9DB54E920EEDBF9945729C`

审计: `tools/audit_v21b.js` PASS。

实机结果: v21b 能刷入，但无法与 Kindle 建立连接。

## v21c (已构建, 待实机)
目标: 排除 Report Map 长度和输出报告变量。

- 复合描述符去掉 LED 输出报告
- 长度 87B -> 67B (原厂键盘描述符 69B)
- 输入仍为 8 字节: modifier + reserved + 4 keys + Consumer usage
- 键表仍为 type 4 Consumer 0x0070/0x006F

| app.bin 偏移 | 原值 | 新值 | 说明 |
|---|---|---|---|
| 0xABBE | `50 74` | `30 74` | 长度 69 -> 67 |
| 0xABC2 | `c1 ff 2a 27 e2 01` | `c1 ff 50 34 e2 01` | 指针 -> 0x1E23450 |
| 0xC7C6 | `e8 58` | `38 59` | Consumer usage1 -> +38 |
| 0x23330 | 00... | 67B | 复合描述符 (app.bin 内) |

产物: `outputs/IINE_Kindle_Click_v21c_Composite.ufw`
SHA-256: `B0EA4DD6C0A03AE0C0BF9A22E8FFF9EC54687BA88D7E7D856D8C490301931214`

审计: `tools/audit_v21c.js` PASS。

## v21d (已构建, 待实机)
目标: 不搬移描述符，直接写回原厂键盘槽位，排除长度/落点变量。

- 67B 复合描述符写入原槽位 `0x2260A` (原槽位 69B)
- 原选择器指针 `0x1E2272A` 不变，只把长度 69 -> 67
- 去掉 LED 输出报告
- 输入仍为 8 字节，键表仍为 type 4 Consumer 0x0070/0x006F

| app.bin 偏移 | 原值 | 新值 | 说明 |
|---|---|---|---|
| 0xABBE | `50 74` | `30 74` | 长度 69 -> 67 |
| 0xC7C6 | `e8 58` | `38 59` | Consumer usage1 -> +38 |
| 0x2260A | 原键盘描述符 | 67B 复合描述符 | 原槽位覆盖 |

产物: `outputs/IINE_Kindle_Click_v21d_Composite.ufw`
SHA-256: `1BA7B3FC2DED26D22987995E339548F2EA49A56EA489BD82EB273D148132F3D2`

审计: `tools/audit_v21d.js` PASS。

## v22 (已构建, 待实机)
基线: v21b (已实机全功能正常)

### 键表

| index | 偏移 | 新条目 | 功能 |
|---:|---|---|---|
| 0 | 0x2289C | `02 00 00 4B 00 00` | A -> 上一页 |
| 1 | 0x228A2 | `02 00 00 4E 00 00` | B -> 下一页 |
| 2 | 0x228A8 | `04 00 30 00 00 00` | 虚拟组合键 -> Power |
| 3 | 0x228AE | `00 00 00 00 00 00` | Y 禁用 |
| 16 | 0x228FC | `02 00 00 4E 00 00` | 上 -> 下一页 |
| 17 | 0x22902 | `02 00 00 4B 00 00` | 下 -> 上一页 |
| 18 | 0x22908 | `04 00 70 00 00 00` | 左 -> 亮度 - |
| 19 | 0x2290E | `04 00 6f 00 00 00` | 右 -> 亮度 + |

### A+B 组合键
- 聚合函数 `0x1E0C818` 前 8 字节改为间接跳转
- `r2 = 0x1E2272A; goto r2`
- Trampoline at VMA `0x1E2272A` / app.bin `0x2260A`
  (v21b 已不再引用的旧键盘描述符槽位)
- `(mask & 3) == 3` 时把 mask 替换为 `4` (虚拟键 2)
- 虚拟键 2 映射 Consumer Power 0x0030

产物: `outputs/IINE_Kindle_Click_v22_ABchord.ufw`
SHA-256: `C05DF35D4C0252A95DC3A45CC47868A939C79D039780A50A1B2E0C3FE27D02D8`

审计: `tools/audit_v22.js` PASS (含 trampoline/描述符/UFW/JLFS CRC)。
已知边界: A+B 松开不同步时可能残留一次翻页; 若实测出现, v22b 加锁存。

实机结果: v22 能连接, 但所有按键失效。组合键 trampoline 的代码落点/
描述符指针错误共同导致失效; 已废弃。根因: v22/v22s 早期版本把
选择器指针写成 `0x1E31A60`，实际描述符在 `0x23330` / VMA `0x1E23450`。

## v22s (已构建, 待实机)
安全键表版: 完全移除 trampoline 和代码入口重定向, 只改 Table1。

| index | 偏移 | 条目 | 功能 |
|---:|---|---|---|
| 0 | 0x2289C | `02 00 00 4B 00 00` | A -> 上一页 |
| 1 | 0x228A2 | `02 00 00 4E 00 00` | B -> 下一页 |
| 2 | 0x228A8 | `00 00 00 00 00 00` | X 禁用 |
| 3 | 0x228AE | `00 00 00 00 00 00` | Y 禁用 |
| 16 | 0x228FC | `02 00 00 4E 00 00` | 上 -> 下一页 |
| 17 | 0x22902 | `02 00 00 4B 00 00` | 下 -> 上一页 |
| 18 | 0x22908 | `04 00 70 00 00 00` | 左 -> 亮度 - |
| 19 | 0x2290E | `04 00 6f 00 00 00` | 右 -> 亮度 + |

描述符/选择器/Consumer 偏移保持 v21b 原样。A+B 熄屏未实现。

产物: `outputs/IINE_Kindle_Click_v22s_keymap.ufw`
SHA-256: `2E517183D309BB2754DD30C6B056F0F6FC05A83335AADB2D82AE88C5E7DDB5FC`

审计: `tools/audit_v22s.js` PASS。

## v23 (已构建, 待实机)
基线: v22s (已实机键位正常)

- 修正描述符选择器指针为 `0x1E23450` (v22/v22s 早期错误为 `0x1E31A60`)
- 加回 A+B 组合键 trampoline:
  - `0x1E0C818` 入口: `r2 = 0x1E2272A; goto r2`
  - Trampoline at VMA `0x1E2272A` / app.bin `0x2260A`
  - `(mask & 3) == 3` -> `r0 = 4` (虚拟键 2)
- 键表 index 2 = Consumer Power 0x0030

产物: `outputs/IINE_Kindle_Click_v23_ABchord.ufw`
SHA-256: `F4B18CD49CF547CADBE465F2C0F1937495761FF72A4D6F84E2E595CE280A6879`

审计: `tools/audit_v22.js` PASS。
