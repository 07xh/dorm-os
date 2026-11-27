package com.tyust.course.academic.plugin

import org.json.JSONArray
import org.json.JSONObject

/**
 * First-party native service entries use the existing ServiceExtensionHost and
 * ServicePluginActivity. They are bundled as trusted app code, not presented as
 * externally signed/official catalog packages.
 */
object BundledServiceProviders {
    const val CLOUD_MESSAGES_ID = "cn.ncwu.xiaoxu.messages"
    const val CLOUD_MESSAGES_PAGE = "messages"
    const val SECOND_CLASS_PAGE = "secondclass"

    fun resolveNativePageId(pkg: PluginPackage, requested: String?): String {
        require(pkg.bundled && pkg.manifest.id == CLOUD_MESSAGES_ID && pkg.manifest.isService)
        val pages = PluginJson.objects(pkg.manifest.service!!.getJSONArray("pages"))
            .map { it.getString("id") }.toSet()
        return requested?.takeIf { it in pages } ?: CLOUD_MESSAGES_PAGE
    }

    internal fun load(readAsset: (String) -> ByteArray): Map<String, PluginPackage> {
        val schema = PluginSchema(JSONObject(
            readAsset("academic-plugin/manifest.schema.json").toString(Charsets.UTF_8)
        ))
        val source = """
            globalThis.plugin = {
              service: {
                page: async function(args) {
                  return { ok: true, data: {
                    pageId: "messages",
                    title: "消息中心",
                    blocks: []
                  }};
                }
              }
            };
        """.trimIndent()
        val manifestJson = JSONObject().apply {
            put("id", CLOUD_MESSAGES_ID)
            put("name", "校序消息中心")
            put("version", "1.0.0")
            put("apiVersion", 3)
            put("kind", "service")
            put("entry", "index.js")
            put("minAppVersionCode", 135)
            put("category", "campus")
            put("capabilities", JSONArray().put("service.page"))
            put("network", JSONArray())
            put("school", JSONObject()
                .put("id", "ncwu")
                .put("name", "华北水利水电大学")
                .put("domain", "dekt.ncwu.edu.cn")
                .put("protocol", "https")
                .put("basePath", "/"))
            put("service", JSONObject()
                .put("schoolIds", JSONArray().put("ncwu"))
                .put("academicHosts", JSONArray()
                    .put("jwmis.ncwu.edu.cn")
                    .put("dekt.ncwu.edu.cn"))
                .put("authentication", JSONObject().put("mode", "none"))
                .put("pages", JSONArray()
                    .put(JSONObject()
                        .put("id", CLOUD_MESSAGES_PAGE)
                        .put("title", "消息中心"))
                    .put(JSONObject()
                        .put("id", SECOND_CLASS_PAGE)
                        .put("title", "第二课堂")))
                .put("entries", JSONArray()
                    .put(JSONObject()
                        .put("id", "messages")
                        .put("title", "消息中心")
                        .put("pageId", CLOUD_MESSAGES_PAGE)
                        .put("icon", "book")
                        .put("order", 20)
                        .put("placements", JSONArray()
                            .put("home").put("schedule").put("grades")))
                    .put(JSONObject()
                        .put("id", SECOND_CLASS_PAGE)
                        .put("title", "第二课堂")
                        .put("pageId", SECOND_CLASS_PAGE)
                        .put("icon", "activity")
                        .put("order", 21)
                        .put("placements", JSONArray().put("home"))))
                .put("actions", JSONArray()))
            put("files", JSONObject().put(
                "index.js", PluginJson.sha256(source.toByteArray(Charsets.UTF_8))
            ))
        }
        val manifest = PluginManifest(manifestJson)
        manifest.validate(schema)
        val digest = PluginJson.sha256(
            (PluginJson.canonical(manifestJson) + "\n" + source).toByteArray(Charsets.UTF_8)
        )
        val pkg = PluginPackage(
            manifest = manifest,
            source = source,
            digest = digest,
            official = false,
            bundled = true
        )
        return mapOf(CLOUD_MESSAGES_ID to pkg)
    }
}
