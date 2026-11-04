package io.github.x07xh.xiaoxu

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// Fixed same-origin Academic OS backend. No school or WeChat secrets stored here.
private const val ORIGIN = "https://dorm-os-dk9.pages.dev"
private const val SESSION = "__Host-xiaoxu_session"
private val TOKEN = Regex("[A-Za-z0-9_-]{43}")
private val ID = Regex("[A-Za-z0-9_-]{8,100}")

data class Course(val title:String, val teacher:String, val location:String,
    val day:Int, val start:Int, val end:Int, val weeks:List<Int>)
data class Assignment(val id:String, val title:String, val course:String,
    val due:String, val done:Boolean, val revision:Int)
data class Notice(val title:String, val date:String, val url:String)
data class Candidate(val id:String, val title:String, val group:String)
data class PrivateData(val courses:List<Course>, val assignments:List<Assignment>,
    val candidates:List<Candidate>, val scheduleStatus:String)
class ExpiredSession: IOException("校序登录已过期，请重新登录")

class NativeApi(context:Context) {
    private val vault=SessionVault(context.applicationContext)
    private val http=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()
    @Volatile private var cookie:String?=vault.load()
    fun hasSession()=cookie!=null

    private fun request(path:String, method:String="GET", json:JSONObject?=null):JSONObject {
        require(path.startsWith("/")&&!path.startsWith("//"))
        val builder=Request.Builder().url(ORIGIN+path)
            .header("Accept","application/json")
            .header("Cache-Control","no-store")
            .header("User-Agent","Xiaoxu-Native-Android/0.2")
        cookie?.let{builder.header("Cookie",it)}
        if(method!="GET") builder.header("Origin",ORIGIN)
        val body=json?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
        builder.method(method,body)
        http.newCall(builder.build()).execute().use { resp ->
            if(resp.code==401)throw ExpiredSession()
            if(resp.code==429)throw IOException("请求频率过高，请稍后再试")
            if(!resp.isSuccessful)throw IOException("校序接口暂不可用（HTTP "+resp.code+"）")
            if(!resp.header("Content-Type","").orEmpty().contains("application/json",true))
                throw IOException("服务没有返回预期的 JSON")
            val raw=resp.body?.source()?.readUtf8LineStrictOrAll(1_000_000)
                ?:throw IOException("服务器没有返回内容")
            if(path=="/api/v1/auth/login"){
                val header=resp.headers.values("Set-Cookie").firstOrNull{
                    it.startsWith(SESSION+"=")
                }?:throw IOException("服务器未提供安全会话")
                val value=header.substringAfter('=').substringBefore(';')
                if(!TOKEN.matches(value))throw IOException("登录会话格式无效")
                cookie=SESSION+"="+value
                vault.save(cookie!!)
            }
            return JSONObject(raw)
        }
    }

    fun login(password:String){
        require(password.isNotBlank())
        request("/api/v1/auth/login","POST",JSONObject()
            .put("passphrase",password).put("durationDays",7))
        if(!verify())throw IOException("校序本人会话验证未通过")
    }
    fun verify():Boolean{
        if(cookie==null)return false
        return try {
            val ok=request("/api/v1/auth/me").optBoolean("authenticated")
            if(!ok)forget()
            ok
        }catch (_:ExpiredSession){forget();false}
    }
    fun forget(){cookie=null;vault.clear()}
    fun logout(){try{if(cookie!=null)request("/api/v1/auth/logout","POST",JSONObject())}finally{forget()}}

    fun notices():List<Notice>{
        val payload=request("/api/notices")
        if(!payload.optBoolean("success"))throw IOException("官方通知暂时无法读取")
        val rows=payload.optJSONArray("items")?:return emptyList()
        return (0 until minOf(rows.length(),60)).mapNotNull{i->
            val x=rows.optJSONObject(i)?:return@mapNotNull null
            val title=x.optString("title")
            if(title.isBlank())return@mapNotNull null
            Notice(title,x.optString("date").ifBlank{x.optString("publishedAt").take(10)},
                x.optString("url").ifBlank{x.optString("sourceUrl")})
        }
    }

    fun privateData():PrivateData{
        val source=request("/api/v1/verified/schedule")
        val status=source.optString("status","not_connected")
        val rows=source.optJSONArray("records")
        val courses=if(status=="ready"||status=="stale")
            (0 until minOf(rows?.length()?:0,250)).mapNotNull{i->
                val x=rows?.optJSONObject(i)?:return@mapNotNull null
                val day=x.optInt("weekday")
                val first=x.optInt("startSection")
                val last=x.optInt("endSection")
                val title=x.optString("title")
                if(day !in 1..7||first !in 1..24||last !in first..24||title.isBlank())
                    return@mapNotNull null
                val ws=x.optJSONArray("weeks")
                Course(title,x.optString("teacher"),x.optString("location"),day,first,last,
                    (0 until (ws?.length()?:0)).map{ws!!.optInt(it)}.filter{it in 1..35})
            }else emptyList()
        val tasks=request("/api/v1/assignments?limit=100&offset=0").optJSONArray("items")
        val assignments=(0 until (tasks?.length()?:0)).mapNotNull{i->
            val x=tasks?.optJSONObject(i)?:return@mapNotNull null
            val id=x.optString("id")
            val title=x.optString("title")
            if(id.isBlank()||title.isBlank())return@mapNotNull null
            Assignment(id,title,x.optString("source_course").takeUnless{it=="null"}.orEmpty(),
                x.optString("due_local_date").takeUnless{it=="null"}.orEmpty(),
                x.optString("local_status")=="done",x.optInt("revision"))
        }
        val inbox=request("/api/v1/wechat/candidates?limit=30&status=needs_review")
            .optJSONArray("items")
        val candidates=(0 until (inbox?.length()?:0)).mapNotNull{i->
            val x=inbox?.optJSONObject(i)?:return@mapNotNull null
            val id=x.optString("id")
            if(id.isBlank())return@mapNotNull null
            Candidate(id,x.optString("title").ifBlank{"待核实消息"},
                x.optString("group_name").takeUnless{it=="null"}.orEmpty())
        }
        return PrivateData(courses,assignments,candidates,status)
    }

    fun addTask(title:String){
        require(title.isNotBlank()&&title.length<=120)
        request("/api/v1/assignments","POST",JSONObject().put("title",title.trim())
            .put("deadline",JSONObject().put("precision","unknown")))
    }
    fun setTaskDone(task:Assignment){
        require(ID.matches(task.id))
        request("/api/v1/assignments/"+task.id,"PATCH",JSONObject()
            .put("revision",task.revision)
            .put("localStatus",if(task.done)"todo" else "done"))
    }
    fun review(item:Candidate,accept:Boolean){
        require(ID.matches(item.id))
        request("/api/v1/wechat/candidates/"+item.id,"POST",JSONObject()
            .put("decision",if(accept)"accept" else "dismiss"))
    }
}

// Android Keystore AES/GCM seals session cookie at rest. No password persistence.
private class SessionVault(context:Context){
    private val pref=context.getSharedPreferences("xiaoxu_native_v1",Context.MODE_PRIVATE)
    private val alias="xiaoxu_native_owner_session_v1"
    private fun secret():SecretKey{
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(alias,null)as? SecretKey)?.let{return it}
        val gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return gen.generateKey()
    }
    fun save(value:String){
        try{
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,secret())
            pref.edit().putString("session",Base64.encodeToString(
                cipher.iv+cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)),
                Base64.NO_WRAP)).apply()
        }catch(_:Exception){clear()}
    }
    fun load():String?{
        val encoded=pref.getString("session",null)?:return null
        return try{
            val bytes=Base64.decode(encoded,Base64.DEFAULT)
            require(bytes.size>=29)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,secret(),
                GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            val value=String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),
                StandardCharsets.UTF_8)
            require(value.startsWith(SESSION+"=")&&TOKEN.matches(value.substringAfter('=')))
            value
        }catch(_:Exception){clear();null}
    }
    fun clear(){pref.edit().remove("session").apply()}
}

// Read up to the API size cap, without logging private payloads.
private fun okio.BufferedSource.readUtf8LineStrictOrAll(cap:Int):String {
    val bytes=readByteArray(cap.toLong().plus(1))
    if(bytes.size>cap)throw IOException("响应超过允许大小")
    return String(bytes,StandardCharsets.UTF_8)
}
