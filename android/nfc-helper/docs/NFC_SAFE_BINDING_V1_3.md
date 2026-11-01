# NFC 写卡与脚本绑定安全门禁 V1.3

## 变更

此前：点击绑定 → 先在 Root 脚本库写入 UUID→脚本关联 → 启动写卡，用户取消或写卡失败可能留下孤儿绑定。

现在：点击绑定 → 启动 Android NFC 写卡 Activity → 写入 NDEF MIME 记录 → 回读 payload 验证 → `RESULT_OK` 返回原 UUID → App 校验 UUID/脚本 ID/结果码 → **才**调用 KSU `--script-bind`。

取消、只读标签、容量不足、非 NDEF、写入失败及回读不同均返回取消结果；不会建立新关联。对于 NdefFormatable 未格式化标签：格式化后先要求再次点击绑定进行写入与回读，不把未验证状态当作成功。

## 兼容

- 新写入 MIME：`application/vnd.dormos.nfc-script`。
- 保留旧 MIME：`application/vnd.dormos.ncwu`，用于以前写过的合法 UUID 标签。
- 两种 MIME 都只允许接受 `v2:tag:<UUID>` 或兼容 `v3:scene:<UUID>` 的 payload。
- UUID 仍可被复制，不可当身份认证凭证；Root 用户可随时禁用全局 NFC 或解除绑定。

## 测试

新增 `NfcProfilesTest.bindingMayCommit` 断言：只有回读验证成功且 UUID 与待登记脚本均有效时允许写入 Root 绑定；取消、无效脚本、ID 不匹配不能提交。

## 未解决的设备门禁

- Ace 6 ColorOS 16 NFC 写卡读回、锁屏与后台启动限制，尚未上真机验证。
- Android 16 的“允许 NFC 启动此 App”受用户系统设置管理。
- 未测待机电量，不能宣称 0% 耗电。
