# 校序 R8 规则（#334）
#
# 正式 Release 启用 R8；保留可还原的崩溃栈。
# Room / Compose / AndroidX 均有自己的 consumer rules，这里只补本项目自己的需要。

# 保留行号，崩溃栈才可读；正式发布时不要为了体积把栈信息压掉。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# TWA / Custom Tabs 走 Intent 与 manifest 元数据，没有反射，无需额外 keep。
# 若后续引入反射序列化，请在此补充对应的 -keep。
