# 项目文件与结果索引

更新: 2026-10-19

## 1. 原厂只读资产

| 路径 | 说明 |
|---|---|
| `analysis/firmware/L1161 L1162.ufw` | 原厂升级包，645,184 B，所有派生的基线 |
| `analysis/firmware/L1161 固件升级程序_1.4.40.exe` | 官方刷机程序 |
| `analysis/firmware/升级步骤.doc` | 原厂升级说明 |
| `analysis/firmware/MANIFEST.md` | 原厂文件 SHA-256 清单 |

原厂 UFW SHA-256:
`73005E7C885AAB2A644ADFDF2DBADC01802B34427E9E6FEBF823F50D4D7745E9`

## 2. 协议与参考项目

| 路径 | 说明 |
|---|---|
| `analysis/protocol/kindle-click-spec.md` | Kindle Click 广播/配对/HID/按键协议规格 |
| `outputs/kindle-click-spec.md` | 协议规格副本 |
| `analysis/reference/Open-Kindle-Click-nRF52840/` | nRF52840 参考实现；`PROTOCOL.md` 为权威协议说明 |
| `analysis/reference/Flipper-Zero-Kindle-Click/` | Flipper Zero 参考实现；`kc_profile.c`、`kindle_click.c` |
| `analysis/reference/Open-Kindle-Click-Android/` | Android 端参考实现 |
| `analysis/reference/fw-AC63_BT_SDK/` | 杰理 AC63 SDK；HID 示例、板级配置、工具 |
| `outputs/REFERENCE-参考项目.md` | 参考项目与许可证摘要 |

## 3. 逆向与证据

| 路径 | 说明 |
|---|---|
| `docs/RE_GROUND_TRUTH.md` | 逆向事实总表：芯片、加密、键表、描述符、PnP |
| `docs/DESCRAMBLE_FINDINGS.md` | UFW 解扰/解密的发现与验证 |
| `docs/FIRMWARE_ANALYSIS.md` | 固件结构、分区、校验链、升级格式 |
| `docs/CHIP_IDENTIFICATION.md` | AC6321A 识别证据 |
| `work/app_dec.lst` | 解密后 app 的 q32s 反汇编 |
| `work/app_dec32.bin` | 解密后 app.bin |
| `work/hid-input.c` | Linux HID usage→evdev 映射参考 |
| `work/ac632n_manual.txt` | AC632N 资料/手册文本 |

## 4. 候选固件报告

| 文档 | 版本/结论 |
|---|---|
| `docs/CANDIDATE_V18_REPORT.md` | v18：PnP 构造函数，按键无效 |
| `docs/CANDIDATE_V19_REPORT.md` | v19：PnP 直写，翻页正常、亮度无效 |
| `docs/CANDIDATE_V20_REPORT.md` | v20：独立 Consumer ID 5，亮度仍无效 |
| `docs/CANDIDATE_V21_REPORT.md` | v21：描述符在 app.bin 外，工具拒绝 |
| `docs/CANDIDATE_V21B_REPORT.md` | v21b：87B 复合描述符，实机全功能正常（旧键位） |
| `docs/CANDIDATE_V21C_REPORT.md` | v21c：67B/无输出报告，中间实验 |
| `docs/CANDIDATE_V21D_REPORT.md` | v21d：67B/原槽位，中间实验 |
| `docs/CANDIDATE_V22_REPORT.md` | v22：A+B 组合键早期版本（指针错误，已废弃） |
| `docs/CANDIDATE_V22S_REPORT.md` | v22s：安全键表版，实机键位正常 |
| `docs/CANDIDATE_V23_REPORT.md` | v23：正确指针 + A+B 组合键，待实机 |

## 5. 安全、恢复与测试

| 路径 | 说明 |
|---|---|
| `docs/BRICK_RISK_V21B.md` | v21b 变砖风险评估 |
| `docs/FLASH_SAFETY_CHECKLIST.md` | 刷写前检查表（当前候选 v23） |
| `docs/RECOVERY_GUIDE.md` | 原厂恢复步骤与故障处理 |
| `docs/TEST_PLAN.md` | 测试计划、通过条件、未验证项 |
| `docs/GPIO_MEASUREMENT_GUIDE.md` | GPIO 测量指引 |
| `analysis/test/v20_audit.log` | v20 离线审计日志 |
| `analysis/test/v21_audit.log` | v21 离线审计日志 |
| `analysis/test/v21d_audit.log` | v21d 离线审计日志 |
| `analysis/test/v22_audit.log` | v22/v23 离线审计日志 |

## 6. 候选固件文件

`release/` 与 `outputs/` 中的 UFW 均为 645,184 B：

| 文件 | SHA-256 | 状态 |
|---|---|---|
| `IINE_Kindle_Click_v18_PnP.ufw` | `AA6E8986E174203B51D8192B28B86B1E053214CCFA07BEB771EC1317D70549B0` | 已实机，按键无效 |
| `IINE_Kindle_Click_v19_PnPdirect.ufw` | `5589B396DD04E5983FD0D6D8A470E824203F5EA3D73BF9E81EED0717F4A11B47` | 已实机，翻页正常、亮度无效 |
| `IINE_Kindle_Click_v20_Consumer.ufw` | `CCAE44F3DFC0D310C61F51A84B93D19D47BC9CAD6C9EF4294A2F504E12F7A519` | 已实机，亮度仍无效 |
| `IINE_Kindle_Click_v21_Composite.ufw` | `F8FBAC88DFB645DB243A733402B44B55DB356508D822A5612CC2BD75C499A5BD` | 工具拒绝识别 |
| `IINE_Kindle_Click_v21b_Composite.ufw` | `29FDADE0BE86BA0AFD27569970D70620EBCBD8A7CE9DB54E920EEDBF9945729C` | 已实机全功能正常（旧键位） |
| `IINE_Kindle_Click_v21c_Composite.ufw` | `B0EA4DD6C0A03AE0C0BF9A22E8FFF9EC54687BA88D7E7D856D8C490301931214` | 中间实验 |
| `IINE_Kindle_Click_v21d_Composite.ufw` | `1BA7B3FC2DED26D22987995E339548F2EA49A56EA489BD82EB273D148132F3D2` | 中间实验 |
| `IINE_Kindle_Click_v22_ABchord.ufw` | `C05DF35D4C0252A95DC3A45CC47868A939C79D039780A50A1B2E0C3FE27D02D8` | 描述符指针错误，已废弃 |
| `IINE_Kindle_Click_v22s_keymap.ufw` | `2E517183D309BB2754DD30C6B056F0F6FC05A83335AADB2D82AE88C5E7DDB5FC` | **已实机键位正常** |
| `IINE_Kindle_Click_v23_ABchord.ufw` | `F4B18CD49CF547CADBE465F2C0F1937495761FF72A4D6F84E2E595CE280A6879` | 待实机（A+B 熄屏） |

## 7. 构建与审计工具

| 脚本 | 说明 |
|---|---|
| `tools/descramble.js` | UFW 头/条目 KS 解扰 |
| `tools/doe32.js` | SFC 密钥流实验 |
| `tools/app_decrypt.js` | app 区解密 |
| `tools/build_kindle_click.js` | 通用补丁/重打包流水线 |
| `tools/build_v22s.js` | 安全键表版构建 |
| `tools/build_v22.js` | A+B 组合键构建（输出 v23） |
| `tools/audit_v22s.js` | 安全键表版审计 |
| `tools/audit_v22.js` | A+B 版审计（含 trampoline） |
| `tools/elf_vma.js` | 反汇编 VMA 修正 |
| `tools/ufw_container.js`、`tools/ufw_crc_*.js` | UFW 结构与 CRC 分析 |
| `tools/appdir2.js`、`tools/sample_table.js` | app 目录/键表分析 |

## 8. 中间产物

`work/` 保存反汇编、解扰、实验对象和临时样本，例如：

- `work/app_dec.lst`：app 反汇编
- `work/app_dec32.bin`：解密后的 app.bin
- `work/ble_app_full.lst`：完整 BLE app 反汇编
- `work/v15_app_decrypted.bin`、`v16_app_decrypted.bin`：历史样本
- `work/wrap_*.s/.o`：反汇编包装
- `work/chord_far.s`、`work/indirect.s`：组合键 trampoline 汇编验证

这些文件是分析证据/中间产物，不是最终发布固件。

## 9. 空目录

- `src/`：源码工程预留（当前未使用）
- `tests/`：自动化测试预留（当前未使用）

## 10. 结果状态说明

- **已实机**：真实设备上刷入并测试过。
- **待实机**：已构建并通过离线审计，但未在设备上验证。
- **已废弃**：保留用于追溯，不要刷写。
- **中间实验**：用于隔离变量，不作为最终版本。

## 11. 其他项目文档

| 路径 | 说明 |
|---|---|
| `README.md` | 项目根目录总览 |
| `docs/PROJECT_INDEX.md` | 本索引 |
| `docs/RESULTS.md` | 版本结果矩阵 |
| `docs/PROJECT_STATUS.md` | 当前阶段与下一步 |
| `docs/FINAL_REPORT.md` | 最终总结 |
| `docs/MODIFICATION_LOG.md` | 全部补丁与版本修改记录 |
| `docs/FEASIBILITY_REPORT.md` | 路线可行性与路线对比 |
| `docs/ROUTE_B_PLAN.md` | 路线 B/SDK 重建计划 |
| `docs/REFERENCE_PROTOCOL.md` | 参考协议/许可证摘要 |
| `docs/BRIGHTNESS_CONSUMER_PLAN.md` | 亮度 Consumer 路径分析 |
| `docs/V18_SAFETY_AUDIT.md` | v18 安全审计 |
| `docs/GPIO_MEASUREMENT_GUIDE.md` | GPIO 测量指引 |
| `release/README.md` | release 目录索引 |
| `release/SHA256SUMS.txt` | release UFW 哈希清单 |
