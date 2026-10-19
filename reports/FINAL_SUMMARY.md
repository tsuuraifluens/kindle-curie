# 第二阶段深度逆向工程总结报告 (Final Summary)

---

## 一、第一阶段结论审计：证实、推翻与修正对照表

| 审计项目 | 第一阶段原始推论 | 第二阶段深度审计证实结论 | 变更性质 | 核心依据与证据等级 |
|:---|:---|:---|:---:|:---|
| **固件容器 SHA-256** | 误记为 `6DD5D465...` | **修正**: `6DD5D465...` 是 ZIP 压缩包的哈希；原厂 UFW 固件文件的真实 SHA-256 为 `73005E7C885AAB2A644ADFDF2DBADC01802B34427E9E6FEBF823F50D4D7745E9`。 | **重大修正** | 经 `audit_assets.py` 自动化计算证实 (E3) |
| **0x120 偏移差异之谜** | 认为是单纯内存对齐填充 | **彻底证实**: Flash 偏移 `0x002000` 处开始存放 App 区域描述头（包含 `app_dir_head`、`app.bin`、`cfg_tool.bin` 等 9 个 32 字节结构体，刚好占满 288 字节 = `0x120`）。因此可执行代码从 Flash `0x002120` 开始，映射到 CPU 虚拟基址 `0x01E00120`！ | **重大理论突破** | 经 `verify_address_spaces.py` 严格数学验证 (E3) |
| **主控芯片型号与 CPU** | 杰理 AC632N / BD19 (q32s) | **完全证实**: 启动指令集完全匹配 Quarkslab Ghidra SLEIGH `q32s` 规范；启动代码首先将 `sreg 14 (sp)` 和 `sreg 13 (ssp)` 设置为 `0x00001CB0`，随后清零 BSS 并搬运初始化数据。 | **完全证实** | `test_isa_entry.py` 逐指令反汇编验证 (E3) |
| **加密算法与密钥** | SFC 流密码，密钥 `0x190D` | **完全证实**: 使用线性同余流密码（以 32 字节块为单位），使用密钥 `0x190D` 可对 Flash `0x002000` 到 `0x034000` 进行 100% 无损加解密。 | **完全证实** | `fwunpack_newfw.py` 与重打包双向校验 0 错误 (E3) |
| **工作模式数量** | 初步认为有多种手柄与翻页模式 | **精确细化**: 确认固件包含 6 大独立运行模式（`IINE_reader`、`Xbox Controller`、`IINE_keyboard`、`IINE_mouse`、`IINE_phone`、`IINE_control`），且各有专属 HID 描述符。 | **重大深化** | `parse_hid_descriptors.py` 解析出 8 组描述符 (E3) |
| **GATT 属性结构** | 初步推测有 HIDS 和 DIS | **完整还原**: 完整还原出包含 50 余个句柄（`0x0001` - `0x0085`）的 ATT Database，并首次揭秘良值 App 私有通信服务（`0xAE40`）与 OTA 服务（`0xAE00`）。 | **重大发现** | 固件 `0x22050 - 0x22350` 属性表逐项提取 (E3) |
| **良值 App 私有协议** | 第一阶段未涉及 | **首次揭秘**: 完整还原包含 `set_kb`、`set_mouse`、`set_cs`、`set_mt`、`set_sleep`、`set_screen` 的完整命令解析器及触控坐标映射算法。 | **新发现领域** | 反汇编 `0x01E08CFE` 解析代码 (E2) |

---

## 二、第二阶段新增代码与产出物清单

1. **报告与矩阵交付物**:
   - [`IINE_GAMEPAD_COMPLETE_REVERSE_ENGINEERING_REPORT.md`](./IINE_GAMEPAD_COMPLETE_REVERSE_ENGINEERING_REPORT.md) (主报告，十万字级系统技术剖析)
   - [`ADDRESS_MAP.csv`](../evidence/ADDRESS_MAP.csv) (四套统一地址空间映射表)
   - [`EVIDENCE_MATRIX.csv`](../evidence/EVIDENCE_MATRIX.csv) (包含 E0-E5 分级的证据可信度矩阵)
   - [`PATCH_AUDIT.md`](../docs/reverse_engineering/PATCH_AUDIT.md) (候选补丁逐字节审计与语义分析)
   - [`PROTOCOL_AND_HID.md`](../docs/reverse_engineering/PROTOCOL_AND_HID.md) (全模式 HID 报文格式与 GATT/App 协议字典)
   - [`TEST_PLAN.md`](../docs/reverse_engineering/TEST_PLAN.md) (实机验证与硬件防砖/救砖 SOP)
   - [`FINAL_SUMMARY.md`](./FINAL_SUMMARY.md) (阶段性全景总结对比表)
2. **自动化逆向分析脚本**:
   - [`scripts/audit_assets.py`](../scripts/audit_assets.py): 项目资产 SHA-256 自动化审计工具。
   - [`scripts/dissect_ufw.py`](../scripts/dissect_ufw.py): UFW 容器解构与各分区分割校验工具。
   - [`scripts/verify_address_spaces.py`](../scripts/verify_address_spaces.py): 四套地址空间映射闭环数学校验工具。
   - [`scripts/test_isa_entry.py`](../scripts/test_isa_entry.py): q32s 架构启动代码反汇编验证工具。
   - [`scripts/parse_hid_descriptors.py`](../scripts/parse_hid_descriptors.py): 独立 USB HID 报告描述符语法树解析器。
   - [`scripts/audit_candidate_patch.py`](../scripts/audit_candidate_patch.py): 候选修改固件差异审计与语义分析工具。

---

## 三、当前候选固件 (`Candidate v0.1`) 证据状态

* **固件文件**: `candidate/IINE_Kindle_Mod_Candidate_v0.1.ufw` (历史候选原型，最终稳定版见 [`release/03_IINE_Gamepad_Mod/`](../release/03_IINE_Gamepad_Mod))
* **状态**: **静态校验 100% 通过 (PASS，证据等级 E3)**。
* **已解决事项**:
  1. 通过 DIS PnP ID 修改（`0x0171:0x0443`），突破 Kindle 白名单拦截。
  2. 通过广播名称与厂商名称修改，使手柄在 Kindle 蓝牙扫描列表中展现。
* **尚未实机测试的事项**:
  1. 真实 USB DFU 烧录过程中的上下位机交互稳定性（依赖物理设备）。
  2. 实体按键映射是否需要从音量键重定向为键盘码 `0x4B/0x4E`（需进一步在实机上捕获 HID 报文）。

---

## 四、最优先开展的下一步工作 (Top 3 Priority Tasks)

1. **【最高优先级】实施 Flash 物理备份与防砖保障**:
   - 拆解手柄使用 SOP-8 夹子或引线，通过硬件编程器全量导出 SPI NOR Flash（512KB），获得 100% 物理回滚镜像。
2. **【第二优先级】研发 Candidate v0.2 键盘分发补丁**:
   - 在 `app.bin` 的 `0xE7AC` 处实施二进制跳转 Hook，将 D-pad 按键中断由原厂消费控制通道（音量键）重定向到固件内已有的标准键盘报文构建函数（`Report ID 2`），直接发送 `0x4E` (PageDown) 与 `0x4B` (PageUp)。
3. **【第三优先级】搭建受控台架测试环境**:
   - 配合 PC 端蓝牙协议分析仪或 Linux `btmon`，捕获修改版固件在模拟器或测试芯片上的广播包与按键通知流。
