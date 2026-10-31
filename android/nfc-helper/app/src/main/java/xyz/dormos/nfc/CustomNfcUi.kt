package xyz.dormos.nfc

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

/**
 * Device-local custom NFC profile manager: no root, background service or sync.
 */
class CustomNfcUi(
    private val activity: Activity,
    private val store: NfcProfileStore,
    private val onWrite: (NfcProfile) -> Unit,
    private val onMessage: (String) -> Unit,
    private val onProfilesChanged: () -> Unit
) {
    private val types = arrayOf(
        "Ncwu Wi-Fi 网络诊断",
        "打开自定义 HTTPS 网页",
        "DORM OS · 学习模式",
        "DORM OS · 休息模式",
        "DORM OS · 开发模式",
        "DORM OS · 出门模式",
        "KernelSU · Wi-Fi 网卡状态（只读 SH）"
    )
    private val scenes = arrayOf("study", "rest", "dev", "away")

    fun openManager() {
        val profiles = store.all()
        val labels = arrayOf("＋ 创建自定义 NFC 标签") +
            profiles.map { it.name + " · " + description(it) }
        AlertDialog.Builder(activity)
            .setTitle("自定义 NFC · 共 " + profiles.size + " 枚")
            .setItems(labels) { _, index ->
                if (index == 0) edit(null) else options(profiles[index - 1])
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun description(profile: NfcProfile): String = when (profile.action) {
        NfcProfiles.DIAGNOSE_NCWU -> "Ncwu 检测"
        NfcProfiles.OPEN_HTTPS -> "打开网页"
        NfcProfiles.DORM_SCENE -> "DORM " + profile.target
        NfcProfiles.KSU_WIFI_STATUS -> "KernelSU 按需 SH 检测"
        else -> "未知动作"
    }

    private fun options(profile: NfcProfile) {
        AlertDialog.Builder(activity)
            .setTitle(profile.name)
            .setItems(arrayOf("写入自己的 NFC 标签", "编辑名称或动作（无需重写标签）", "删除绑定")) { _, index ->
                when (index) {
                    0 -> onWrite(profile)
                    1 -> edit(profile)
                    2 -> AlertDialog.Builder(activity)
                        .setTitle("删除 " + profile.name + "？")
                        .setMessage("删除后，已写入的标签不会被擦除，但此手机将不再执行该标签。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除") { _, _ ->
                            if (store.remove(profile.id)) {
                                onProfilesChanged()
                                onMessage("绑定已删除；实体标签未改写。")
                                openManager()
                            } else onMessage("删除失败，请重试。")
                        }.show()
                }
            }.setNegativeButton("返回", null).show()
    }

    private fun edit(old: NfcProfile?) {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 8)
        }
        val name = EditText(activity).apply {
            hint = "例如：书桌 · 学习"
            setSingleLine(true)
            setText(old?.name ?: "")
        }
        val type = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, types)
        }
        val target = EditText(activity).apply {
            hint = "https://example.com/page"
            setSingleLine(true)
            setText(if (old?.action == NfcProfiles.OPEN_HTTPS) old.target else "")
        }
        val note = TextView(activity).apply {
            text = "标签只存随机编号，操作保存在此手机。KernelSU 动作需要另装可选模块并手动启用 Root 权限，不支持执行自填 SH。修改动作不必重写标签。"
            textSize = 13f
        }
        layout.addView(name)
        layout.addView(type)
        layout.addView(target)
        layout.addView(note)
        val selection = when (old?.action) {
            NfcProfiles.OPEN_HTTPS -> 1
            NfcProfiles.KSU_WIFI_STATUS -> 6
            NfcProfiles.DORM_SCENE -> scenes.indexOf(old.target).let { if (it < 0) 2 else it + 2 }
            else -> 0
        }
        type.setSelection(selection)
        type.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                target.visibility = if (position == 1) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (old == null) "新增自定义标签" else "编辑 " + old.name)
            .setView(ScrollView(activity).apply { addView(layout) })
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val index = type.selectedItemPosition
                val action = when (index) {
                    0 -> NfcProfiles.DIAGNOSE_NCWU
                    1 -> NfcProfiles.OPEN_HTTPS
                    6 -> NfcProfiles.KSU_WIFI_STATUS
                    else -> NfcProfiles.DORM_SCENE
                }
                val destination = when (index) {
                    0 -> ""
                    1 -> target.text.toString().trim()
                    6 -> ""
                    else -> scenes.getOrNull(index - 2) ?: ""
                }
                val record = NfcProfile(
                    old?.id ?: NfcProfiles.makeId(),
                    name.text.toString().trim(), action, destination
                )
                if (!NfcProfiles.validProfile(record)) {
                    onMessage("无效配置：名称最多 40 字；网页只能填写不含凭据的 HTTPS 链接。")
                    return@setOnClickListener
                }
                if (store.save(record)) {
                    dialog.dismiss()
                    onProfilesChanged()
                    onMessage(if (old == null) "已创建。请在管理列表里选择「写入」。" else "绑定已更新，无需重新写卡。")
                    openManager()
                } else {
                    onMessage("保存失败：最多支持 32 个标签配置。")
                }
            }
        }
        dialog.show()
    }
}
