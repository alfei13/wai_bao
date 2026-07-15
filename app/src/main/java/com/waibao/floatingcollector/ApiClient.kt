package com.waibao.floatingcollector

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val API_TAG = "ApiClient"
private const val BASE_URL = "https://zhenxian-portal.mariflux.cn/api/admin/v1/fetch"

// 接口认证凭证
private const val APP_KEY = "ppMiFbGtYFEtY85P"
private const val APP_SECRET = "lwfwxSq5WPQGPOEhC0yimKBo215IcE1Y"

// ===== 数据类 =====

data class SkuSource(
    val sourceId: String,
    val sourceName: String,
    val collectMethod: String,
    val collectParams: String   // JSON 字符串，按需解析
)

data class SkuConfig(
    val skuId: String,
    val skuName: String,
    val unit: String,
    val sources: List<SkuSource>
)

/** 上报数据项 */
data class UploadItem(
    val skuId: String,
    val sourceSkuName: String,
    val sourceId: String,
    val priceValue: Double,
    val priceUnit: String,
    val priceDate: String,
    val remark: String = ""
)

/** 上报结果 */
data class UploadResult(
    val success: Boolean,
    val accepted: Int = 0,
    val rejected: Int = 0,
    val message: String = ""
)

// ===== 网络客户端 =====

object ApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // ===== 上报接口 =====

    /**
     * 上报采集数据（同步方法，需在子线程调用）
     */
    fun uploadCollectedData(
        batchId: String,
        sourceCode: String,
        sourceName: String,
        fetchTime: String,
        items: List<UploadItem>
    ): UploadResult {
        val url = "$BASE_URL/data-ingest"
        Log.i(API_TAG, "上报: POST $url, batchId=$batchId, items=${items.size}")

        // 构建 JSON
        val json = JSONObject().apply {
            put("batchId", batchId)
            put("sourceCode", sourceCode)
            put("sourceName", sourceName)
            put("fetchTime", fetchTime)
            put("totalCount", items.size)
            put("items", JSONArray().apply {
                for (item in items) {
                    put(JSONObject().apply {
                        put("skuId", item.skuId)
                        put("sourceSkuName", item.sourceSkuName)
                        put("sourceId", item.sourceId)
                        put("priceValue", item.priceValue)
                        put("priceUnit", item.priceUnit)
                        put("priceDate", item.priceDate)
                        put("remark", item.remark)
                    })
                }
            })
        }

        val body = json.toString()
            .toRequestBody("application/json".toMediaType())
        // 打印完整请求参数
        Log.i(API_TAG, "上报请求体: ${json.toString(2)}")
        val request = Request.Builder()
            .url(url)
            .addHeader("AppKey", APP_KEY)
            .addHeader("AppSecret", APP_SECRET)
            .post(body)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: run {
                Log.w(API_TAG, "上报响应体为空")
                return UploadResult(false, message = "响应体为空")
            }
            Log.i(API_TAG, "上报响应 httpCode=${response.code}, body=$respBody")
            // 容错: 尝试解析 JSON
            if (!respBody.trimStart().startsWith("{")) {
                // 响应体不是 JSON，根据 HTTP 状态码判断
                val httpOk = response.code in 200..299
                Log.w(API_TAG, "响应体非JSON，HTTP状态码=${response.code}")
                return UploadResult(httpOk, message = "HTTP ${response.code}: ${respBody.take(100)}")
            }
            val respJson = JSONObject(respBody)
            val code = respJson.optInt("code", -1)
            // 20000=成功, 20001=部分成功, 200=旧版成功
            val isSuccess = code == 20000 || code == 20001 || code == 200
            if (!isSuccess) {
                return UploadResult(false, message = "业务码异常: $code, msg=${respJson.optString("message")}")
            }
            val result = respJson.optJSONObject("result")
            val accepted = result?.optInt("accepted", 0) ?: 0
            val rejected = result?.optInt("rejected", 0) ?: 0
            val details = result?.optJSONArray("details")
            if (details != null && details.length() > 0) {
                Log.i(API_TAG, "上报详情:")
                for (i in 0 until details.length()) {
                    Log.i(API_TAG, "  ${details.getJSONObject(i)}")
                }
            }
            UploadResult(true, accepted, rejected, respJson.optString("message", "ok"))
        } catch (e: Exception) {
            Log.e(API_TAG, "上报异常: ${e.message}", e)
            UploadResult(false, message = "异常: ${e.message}")
        }
    }

    // ===== 拉取配置接口 =====

    /**
     * 从服务端拉取 SKU 采集配置（同步方法，需在子线程调用）
     * @return SKU 配置列表，失败返回 null
     */
    fun fetchSkuConfig(): List<SkuConfig>? {
        val url = "$BASE_URL/get-sku-config"
        Log.i(API_TAG, "请求: GET $url")
        val request = Request.Builder()
            .url(url)
            .addHeader("AppKey", APP_KEY)
            .addHeader("AppSecret", APP_SECRET)
            .get()
            .build()
        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: run {
                Log.w(API_TAG, "响应体为空")
                return null
            }
            Log.i(API_TAG, "响应 code=${response.code}, body长度=${body.length}")
            if (response.code != 200) {
                Log.w(API_TAG, "HTTP 状态码异常: ${response.code}")
                return null
            }
            parseSkuConfigResponse(body)
        } catch (e: Exception) {
            Log.e(API_TAG, "请求异常: ${e.message}", e)
            null
        }
    }

    /**
     * 解析接口响应 JSON
     * 响应格式: { "code": 200, "message": "OK", "result": [...] }
     */
    private fun parseSkuConfigResponse(body: String): List<SkuConfig>? {
        return try {
            val json = JSONObject(body)
            val code = json.optInt("code", -1)
            if (code != 200) {
                Log.w(API_TAG, "业务码异常: code=$code, message=${json.optString("message")}")
                return null
            }
            val resultArray: JSONArray = json.optJSONArray("result") ?: run {
                Log.w(API_TAG, "result 字段不存在或不是数组")
                return null
            }
            val list = mutableListOf<SkuConfig>()
            for (i in 0 until resultArray.length()) {
                val skuJson = resultArray.getJSONObject(i)
                val skuId = skuJson.optString("skuId", "")
                val skuName = skuJson.optString("skuName", "")
                val unit = skuJson.optString("unit", "")
                val sourcesArray = skuJson.optJSONArray("sources") ?: JSONArray()
                val sources = mutableListOf<SkuSource>()
                for (j in 0 until sourcesArray.length()) {
                    val srcJson = sourcesArray.getJSONObject(j)
                    sources.add(
                        SkuSource(
                            sourceId = srcJson.optString("sourceId", ""),
                            sourceName = srcJson.optString("sourceName", ""),
                            collectMethod = srcJson.optString("collectMethod", ""),
                            collectParams = srcJson.optString("collectParams", "")
                        )
                    )
                }
                list.add(SkuConfig(skuId, skuName, unit, sources))
            }
            Log.i(API_TAG, "解析成功，共 ${list.size} 个 SKU")
            for (sku in list) {
                Log.i(API_TAG, "  ${sku.skuId} ${sku.skuName} (${sku.unit}) - ${sku.sources.size} 个来源")
            }
            list
        } catch (e: Exception) {
            Log.e(API_TAG, "JSON 解析异常: ${e.message}", e)
            null
        }
    }
}
