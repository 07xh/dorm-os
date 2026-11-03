# 校序 M8｜华水二课真实查询结构适配

## 本轮实现
- 新增 lib/academic-second-class-source-map.mjs：字段映射、分页校验和受信任适配器包装。
- 仅转换在本人授权页面证实的活动 ID、名称、日期、学时、积分、学分、审核/活动状态。
- 枚举状态显式映射；未知的二课状态保留 unknown，不猜已完成。
- 用户学号、手机、会话、签名 URL、原始 HTML 等不进入 D1。
- 学时/积分拒绝带单位的非纯数字字符串、负值或越界数值。
- 需要明确的完整分页与空列表证明；失败不会覆盖此前的有效批次。
- 这是无外网请求的结构适配工具，**不代表已经发现学校真实 API，更不代表后台自动同步**。

## 本人正常登录后提供什么
1. 从「我的活动」页面确认查询请求**方法及脱敏 URL 路径**。
2. 返回 JSON 中列表路径、活动 ID、标题、日期、学时/积分、两种状态的字段名及类型。
3. 通过正常翻页确认分页字段、结束条件和空数据如何明确表示。
4. 在「学时/积分汇总」另外核验官方累计；不把候选学时或明细合计冒充学校累计值。
5. 只分享手动整理的脱敏字段结构，不发完整 HAR、Cookie、Token、SSO 跳转、学号或签到二维码。

## 合成映射示例（不是华水真实接口）
~~~js
const mapping = {
  schema: 'xiaoxu/second-class-readonly-map-v1',
  listPath: 'data.items',
  completePath: 'data.finished',
  completeWhen: true,
  nextCursorPath: 'data.nextCursor',
  verifiedEmptyPath: 'data.emptyConfirmed',
  fields: {
    activityId: 'id',
    title: 'name',
    date: 'activityDate',
    hours: 'earned.hours',
    points: 'earned.points',
    activityStatus: 'participation',
    reviewStatus: 'audit.result'
  },
  statusValues: {
    activityStatus: {enrolled: 'registered', finished: 'completed'},
    reviewStatus: {waiting: 'pending', pass: 'approved'}
  }
};
~~~

以上映射要替换成实际页面验证出的真实字段。
受信任适配器的 verifySession 和 fetchPage 只应由本人控制的服务端/本地组件实现，
不接受浏览器上报的认证布尔值，不接收任意来源网址，也不存储学校会话。

## 当前剩余阻断
- 华水本人实际只读业务请求和字段尚未取得；
- 无合法持久会话、无人值守 Cron 以及生产 D1/Access 的实测证据；
- 已有 GET /api/v1/second-class 读取的是本人保存的 D1 数据，不是对学校的实时请求。

## 测试
node scripts/xiaoxu-second-class-mapping-tests.mjs
验证两页、空结果证据、敏感字段丢弃、未知状态、非法时间/数字和原子写入回滚。
