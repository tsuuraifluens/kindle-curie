# 项目总体状态

更新时间: 2026-10-19 (v23 A+B 组合键)

项目总览: `README.md`
文件索引: `docs/PROJECT_INDEX.md`
版本结果矩阵: `docs/RESULTS.md`

## 当前阶段
- 阶段 0-3: **完成**
- 阶段 1 芯片识别: **完成** — AC6321A (AC632N/BD19, q32s, 73KB SRAM)
- 阶段 2 路线: **完成并已实机验证** — 原厂固件二进制补丁 + UFW 重打包
- 阶段 4 按键/蓝牙逆向: **基本完成** — 键表、描述符、报告发送路径已定位
- 阶段 5 固件构建: **完成** — v22s 已实机键位正常; v23 已构建
- 阶段 6 测试: **部分** — v22s 键位正常; 待 v23 A+B 实测
- 阶段 7 刷写安全: **已建立审计与恢复文档**
- 阶段 8 许可证/安全审计: **部分**

## 已确认事实
- 芯片: 杰理 AC6321A; Flash 丝印 ZJZV, 容量 512KB/1MB 尚有冲突证据
  (key_mac 位于 0x7F400, 总镜像 0x9D840 指向 >=1MB); 不影响
  当前 UFW 重打包。
- 原厂 UFW: 645,184 B, SHA-256
  `73005E7C885AAB2A644ADFDF2DBADC01802B34427E9E6FEBF823F50D4D7745E9`
- App 区 SFC 加密: ChipKey `0x190D`, 32B 块。
- UFW/JLFS 校验链已复现; 重打包结果曾与参考 v1.7 逐字节一致。
- v19 实机: Kindle 连接正常, A/B 与方向键上下翻页正常; 亮度无效。
- v20 实机: 翻页正常; 独立 Consumer Report ID 5 的亮度仍无效。
- v22s 实机: A/B/方向键/亮度全部正常。
- 静态证据: 键表 type 4 -> Consumer 分支; v21b 把 usage 写入键盘
  报告 key slot 5/6, 与键盘共用 Report ID 2/collection。
  见 `docs/CANDIDATE_V21B_REPORT.md`。

## 当前候选
- `outputs/IINE_Kindle_Click_v23_ABchord.ufw`
- 大小 645,184 B
- SHA-256
  `F4B18CD49CF547CADBE465F2C0F1937495761FF72A4D6F84E2E595CE280A6879`
- 修改: v22s 键表 + A+B 组合键 trampoline
- 描述符指针: `0x1E23450` (已修正)
- 映射: A 上一页, B 下一页, A+B 熄屏, 上/下翻页, 左/右亮度
- 审计: `tools/audit_v22.js` 全部 PASS

已知可用回退:
- `release/IINE_Kindle_Click_v22s_keymap.ufw`
  SHA-256 `2E517183D309BB2754DD30C6B056F0F6FC05A83335AADB2D82AE88C5E7DDB5FC`

## 阻塞/待验证
- **变砖风险**: 见 `docs/BRICK_RISK_V21B.md`; 结论为低风险,
  但仍要求人工确认原厂备份并禁止自动刷写。
- **连接/亮度**: v21b 已实机全功能正常, 不再改描述符。
- **A+B 组合键**: v23 用正确描述符指针 + 旧描述符槽位 trampoline,
  待实机确认触发与松开行为。
- **亮度长按重复**: v21 已满足“同一 collection”的结构要求,
  理论上可获得 EV_REP; 仍需实机确认。
- **恢复路径**: 官方工具会擦除整片 Flash; 刷写前必须确认工作区外
  原厂备份可用。

## 下一步
1. 人工刷入 v23 (Codex 不自动刷写)。
2. Kindle 上忽略旧设备后重新配对。
3. 验证翻页、左右亮度、长按连续调节、断连重连。
4. 测试 A/B、方向键、亮度、A+B 熄屏。
5. 若 A+B 松开误翻页或 Power 重复, 加组合键锁存。
