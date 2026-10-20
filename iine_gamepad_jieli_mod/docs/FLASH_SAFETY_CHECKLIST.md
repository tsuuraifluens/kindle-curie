# 刷写安全检查表

候选: `outputs/IINE_Kindle_Click_v24_FastConnect.ufw`
日期: 2026-10-19

## 结论
**候选固件本身可进入“人工刷写评估”；Codex 不自动刷写。**

理由: v24 在已实机验证的 v23 基础上，仅修改了 3 个字节的广播参数（广播间隔 500ms -> 20ms、直通 ADV_IND），重算了完整的 JLFS/UFW 校验链；启动区、uboot、isd_config、cfg_tool、p11、flash 参数与设备唯一数据均未改变。v21b/v22s/v23 已实际刷入并正常启动，说明该设备的升级链路 100% 可靠。

## 设备与文件
- 设备: 良值 L1161/L1162 迷你蓝牙手柄
- 芯片: AC6321A (AC632N/BD19)
- 升级模式 USB: `VID_4C4A&PID_3442`
- 候选固件: `outputs/IINE_Kindle_Click_v25_SleepLockout.ufw`
- 候选大小: 645,184 B
- 候选 SHA-256:
  `F67CD51ECB0980587F22E14FD0966762576BAEE0E5AF2E7B1DD79FE31B0E5F53`
- 已知可用回退:
  `IINE_Kindle_Click_v24_FastConnect.ufw` (`5E7448EF3AFB53671FF4E2A56743659FED9A916FF5F937E2FBE0F235690F2072`)
  `IINE_Kindle_Click_v23_ABchord.ufw` (`F4B18CD49CF547CADBE465F2C0F1937495761FF72A4D6F84E2E595CE280A6879`)
  `IINE_Kindle_Click_v22s_keymap.ufw` (`2E517183D309BB2754DD30C6B056F0F6FC05A83335AADB2D82AE88C5E7DDB5FC`)
- 原厂 SHA-256:
  `73005E7C885AAB2A644ADFDF2DBADC01802B34427E9E6FEBF823F50D4D7745E9`

## 检查项
- [x] 芯片型号确认 (AC6321A)
- [x] UFW 容器结构/条目数/总长正确
- [x] UFW 主头 CRC、条目列表 CRC 复算一致
- [x] 条目 0 flash 镜像 CRC 一致
- [x] JLFS 四层 CRC 复算一致
- [x] 预期补丁逐字节匹配
- [x] 解密后 app 区无预期外修改
- [x] app 区外 (boot/uboot/isd_config/cfg_tool/p11/尾部) 逐字节一致
- [x] 未修改蓝牙地址/配对密钥/校准区
- [x] 新 HID 描述符位于 app.bin 内部 0x23330 (原厂全 0 空隙)
- [x] HID 描述符解析: Report ID 2, Input 8B, Output 1B, Consumer page
- [x] 未修改启动区、引导程序、安全配置
- [ ] 工作区外原厂备份已再次核对 (人工确认)
- [ ] 原厂恢复路径已在本轮刷写前复核 (人工确认)

## 写入范围与擦除
- 官方升级程序会按整片 Flash 流程处理 (文档记录为“擦除整个FLASH”)。
- 设备唯一数据是否在整片擦除后重新生成，已有 v18/v19 刷写结果作为
  间接证据，但本轮仍要求先确认原厂 UFW 可恢复。

## 人工刷写步骤
1. 断开所有其他 USB 存储设备，避免盘符冲突。
2. 手柄进入升级模式 (插 USB 后按住“配对+B”约 2 秒)。
3. 打开官方 `L1161 固件升级程序_1.4.40.exe`。
4. 选择 `outputs/IINE_Kindle_Click_v23_ABchord.ufw`。
5. 勾选“成功后复位”；擦除选项按官方默认流程。
6. 点击升级，等待完成，不要中途拔线。
7. 失败立即停止，使用原厂 UFW 恢复，不反复烧录。

## 失败处理
- 能进升级模式: 直接用原厂 UFW 恢复。
- 不能进升级模式: 按 `docs/RECOVERY_GUIDE.md` 的 Mask ROM/短接方案处理。

## 刷后验证
- 能否正常开机/广播/连接
- A/B 与方向键上下翻页
- 方向键左右亮度
- 断连重连
- 充电与电源管理
