import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.corner.catvod.enum.bean.Site
import com.corner.catvod.enum.bean.Vod
import com.corner.catvod.enum.bean.Vod.Companion.setVodFlags
import com.corner.catvodcore.bean.Collect
import com.corner.catvodcore.bean.Result
import com.corner.catvodcore.bean.Url
import com.corner.catvodcore.bean.add
import com.corner.catvodcore.config.api
import com.corner.catvodcore.config.getSite
import com.corner.catvodcore.config.getSpider
import com.corner.catvodcore.config.setRecent
import com.corner.catvodcore.util.Http
import com.corner.catvodcore.util.Jsons
import com.corner.catvodcore.util.Utils
import com.github.catvod.crawler.Spider
import com.github.catvod.crawler.SpiderDebug
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.encodeToString
import okhttp3.Headers.Companion.headersOf
import okhttp3.Headers.Companion.toHeaders
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory


/**
@author heatdesert
@date 2023-12-21 20:47
@description
 */
object SiteViewModel {
    private val log = LoggerFactory.getLogger("SiteViewModel")
//    val episode: MutableState<Episode?> = mutableStateOf<Episode?>(null)
    val result: MutableState<Result> by lazy {  mutableStateOf(Result())}
    val detail: MutableState<Result> by lazy {  mutableStateOf(Result())}
    val player: MutableState<Result> by lazy {  mutableStateOf(Result())}
    val search: MutableList<Collect> = mutableStateListOf(Collect.all())
    val quickSearch: MutableList<Collect> = mutableStateListOf(Collect.all())

    private val supervisorJob = SupervisorJob()
    val viewModelScope = CoroutineScope(Dispatchers.Default + supervisorJob)

    fun homeContent(): Result {
        val site: Site = api?.home?.value ?: return result.value
        result.value = Result()
        try {
            when (site.type) {
                3 -> {
                    val spider = getSpider(site)
                    val homeContent = spider.homeContent(true)
                    SpiderDebug.log("home:$homeContent")
                    setRecent(site)
                    val rst: Result = Jsons.decodeFromString<Result>(homeContent)
                    if ((rst.list.size) > 0) result.value = rst
                    val homeVideoContent = spider.homeVideoContent()
                    SpiderDebug.log(homeVideoContent)
                    rst.list.addAll(Jsons.decodeFromString<Result>(homeContent).list)
                    return rst.also { this.result.value = it }
                }

                4 -> {
                    val params: MutableMap<String, String> =
                        mutableMapOf()
                    params.put("filter", "true")
                    val homeContent = call(site, params, false)
                    SpiderDebug.log(homeContent)
                    return Jsons.decodeFromString<Result>(homeContent).also { this.result.value = it }
                }

                else -> {
                    val homeContent: String =
                        Http.newCall(site.api, site.header?.toHeaders() ?: headersOf()).execute().body.string()
                    SpiderDebug.log(homeContent)
                    return fetchPic(site, Jsons.decodeFromString<Result>(homeContent)).also { result.value = it }
                }
            }
        } catch (e: Exception) {
            log.error("home Content site:{}", site.name, e)
            return Result()
        }
    }

    fun detailContent(key: String, id: String): Result? {
        val site: Site = api?.sites?.find { it.key == key } ?: return null
        try {
            if (site.type == 3) {
                val spider: Spider = getSpider(site)
                val detailContent = spider.detailContent(listOf(id))
                SpiderDebug.log("detail:$detailContent")
                setRecent(site)
                val rst = Jsons.decodeFromString<Result>(detailContent)
                if (!rst.list.isEmpty()) rst.list.get(0).setVodFlags()
                //            if (!rst.list.isEmpty()) checkThunder(rst.list.get(0).vodFlags())
                return rst.also { detail.value = it }
            } else if (site.key.isEmpty() && site.name.isEmpty() && key == "push_agent") {
                val vod = Vod()
                vod.vodId = id
                vod.vodName = id
                vod.vodPic = "https://pic.rmb.bdstatic.com/bjh/1d0b02d0f57f0a42201f92caba5107ed.jpeg"
                //            vod.vodFlags = (Flag.create(ResUtil.getString(R.string.push), ResUtil.getString(R.string.play), id))
                //            checkThunder(vod.getVodFlags())
                val rs = Result()
                rs.list = mutableListOf(vod)
                return rs.also { detail.value = it }
            } else {
                val params: MutableMap<String, String> =
                    mutableMapOf()
                params.put("ac", if (site.type == 0) "videolist" else "detail")
                params.put("ids", id)
                val detailContent = call(site, params, true)
                SpiderDebug.log(detailContent)
                val rst = Jsons.decodeFromString<Result>(detailContent)
                if (!rst.list.isEmpty()) rst.list.get(0).setVodFlags()
                //            if (!rst.list.isEmpty()) checkThunder(rst.list.get(0).getVodFlags())
                return rst.also { detail.value = it }
            }
        } catch (e: Exception) {
            log.error("${site.name} detailContent 异常", e)
            return null
        }
    }

    fun playerContent(key: String, flag: String, id: String): Result? {
//        Source.get().stop()
        val site: Site = getSite(key) ?: return null
        try {
            if (site.type == 3) {
                val spider: Spider = getSpider(site)
                val playerContent = spider.playerContent(flag, id, api?.flags?.toList())
                SpiderDebug.log("player:$playerContent")
                setRecent(site)
                val result = Jsons.decodeFromString<Result>(playerContent)
                if (StringUtils.isNotBlank(result.flag)) result.flag = flag
    //            result.setUrl(Source.get().fetch(result))
    //            result.url.replace() = result.url.v()
                result.header = site.header
                result.key = key
                return result
            } else if (site.type == 4) {
                val params = mutableMapOf<String, String>()
                params.put("play", id)
                params.put("flag", flag)
                val playerContent = call(site, params, true)
                SpiderDebug.log(playerContent)
                val result = Jsons.decodeFromString<Result>(playerContent)
                if (StringUtils.isNotBlank(result.flag)) result.flag = flag
    //            result.setUrl(Source.get().fetch(result))
                result.header = site.header
                return result
            } /*else if (site.isEmpty() && key == "push_agent") {
                val result = Result<Any>()
                result.setParse(0)
                result.setFlag(flag)
                result.setUrl(Url.create().add(id))
                result.setUrl(Source.get().fetch(result))
                return result
            }*/ else {
                var url: Url = Url().add(id)
                val type: String? = Url(id).parameters.get("type")
                if (type != null && type == "json") {
                    val string = Http.newCall(id, site.header?.toHeaders() ?: headersOf()).execute().body.string()
                    if (StringUtils.isNotBlank(string)) {
                        url = Jsons.decodeFromString<Result>(string).url!!
                    }
                }
                val result = Result()
                result.url = url
                result.flag = flag
                result.header = site.header
                result.playUrl = site.playUrl
                result.parse = (if (/*Sniffer.isVideoFormat(url.v())*//* && */StringUtils.isBlank(result.playUrl)) 0 else 1)
    //            result.setParse(if (Sniffer.isVideoFormat(url.v()) && result.getPlayUrl().isEmpty()) 0 else 1)
                return result
            }
        } catch (e: Exception) {
            log.error("${site.name} player error:",e)
            return null
        }
    }


    fun searchContent(site: Site, keyword: String, quick: Boolean) {
        try {
            if (site.type == 3) {
                val spider: Spider = getSpider(site)
                val searchContent = spider.searchContent(keyword, quick)
                SpiderDebug.log(site.name + "," + searchContent)
                val result = Jsons.decodeFromString<Result>(searchContent)
                post(site, result, quick)
            } else {
                val params = mutableMapOf<String, String>()
                params.put("wd", keyword)
                params.put("quick", quick.toString())
                val searchContent = call(site, params, true)
                SpiderDebug.log(site.name + "," + searchContent)
                val result = Jsons.decodeFromString<Result>(searchContent)
                post(site, fetchPic(site, result), quick)
            }
        } catch (e: Exception) {
            log.error("${site.name} search error", e)
        }
    }

    fun searchContent(site: Site, keyword: String, page: String) {
        try {
            if (site.type == 3) {
                val spider: Spider = getSpider(site)
                val searchContent = spider.searchContent(keyword, false, page)
                SpiderDebug.log(site.name + "," + searchContent)
                val rst = Jsons.decodeFromString<Result>(searchContent)
                for (vod in rst.list) vod.site = site
                result.value = rst
            } else {
                val params = mutableMapOf<String, String>()
                params.put("wd", keyword)
                params.put("pg", page)
                val searchContent = call(site, params, true)
                SpiderDebug.log(site.name + "," + searchContent)
                val rst: Result = fetchPic(site, Jsons.decodeFromString<Result>(searchContent))
                for (vod in rst.list) vod.site = site
                result.value = rst
            }
        } catch (e: Exception) {
            log.error("${site.name} searchContent error", e)
        }
    }

    fun categoryContent(key: String, tid: String?, page: String?, filter: Boolean, extend: HashMap<String?, String?>) {
        val site: Site = getSite(key) ?: return
          try {
            if (site.type == 3) {
                val spider: Spider = getSpider(site)
                val categoryContent = spider.categoryContent(tid, page, filter, extend)
                SpiderDebug.log(categoryContent)
                setRecent(site)
                result.value = Jsons.decodeFromString<Result>(categoryContent)
            } else {
                val params = mutableMapOf<String, String>()
                if (site.type == 1 && extend.isNotEmpty()) params.put("f",Jsons.encodeToString(extend))
                else if (site.type == 4) params.put("ext", Utils.base64(Jsons.encodeToString(extend)))
                params.put("ac", if (site.type == 0) "videolist" else "detail")
                params.put("t", tid ?: "")
                params.put("pg", page ?: "")
                val categoryContent = call(site, params, true)
                SpiderDebug.log(categoryContent)
                result.value = Jsons.decodeFromString<Result>(categoryContent)
            }
        } catch (e: Exception) {
            log.error("${site.name} category error", e)
        }
    }


    private fun post(site: Site, result: Result, quick: Boolean) {
        if (result.list.isEmpty()) return
        for (vod in result.list) vod.site = site
        if(quick){
            quickSearch.add(Collect.create(result.list))
            // 同样的数据添加到全部
            quickSearch.get(0).getList()?.addAll(result.list)
        }else{
            search.add(Collect.create(result.list))
            // 同样的数据添加到全部
            search.get(0).getList()?.addAll(result.list)
        }
    }

    fun clearSearch() {
        search.clear()
        search.add(Collect.all())
    }

    fun clearQuickSearch() {
        quickSearch.clear()
        quickSearch.add(Collect.all())
    }
}


private fun call(site: Site, params: MutableMap<String, String>, limit: Boolean): String {
    val call: okhttp3.Call = if (fetchExt(site, params, limit).length <= 1000) Http.newCall(
        site.api,
        site.header?.toHeaders() ?: headersOf(),
        params
    ) else Http.newCall(site.api, site.header?.toHeaders() ?: headersOf(), params)
    return call.execute().body.string()
}

private fun fetchExt(site: Site, params: MutableMap<String, String>, limit: Boolean): String {
    var extend: String = site.ext
    if (extend.startsWith("http")) extend = fetchExt(site)
    if (limit && extend.length > 1000) extend = extend.substring(0, 1000)
    if (!extend.isEmpty()) params.put("extend", extend)
    return extend
}

private fun fetchExt(site: Site): String {
    val res: okhttp3.Response = Http.newCall(site.ext, site.header?.toHeaders() ?: headersOf()).execute()
    if (res.code != 200) return ""
    site.ext = res.body.string()
    return site.ext
}

private fun fetchPic(site: Site, result: Result): Result {
    if (result.list.isEmpty() || StringUtils.isEmpty(result.list.get(0).vodPic)) return result
    val ids = ArrayList<String>()
    for (item in result.list) ids.add(item.vodId)
    val params: MutableMap<String, String> = mutableMapOf()
    params.put("ac", if (site.type == 0) "videolist" else "detail")
    params.put("ids", StringUtils.join(ids, ","))
    val response: String =
        Http.newCall(site.api, site.header?.toHeaders() ?: headersOf(), params).execute().body.string()
    result.list.clear()
    result.list.addAll(Jsons.decodeFromString<Result>(response).list)
    return result
}
//
//@Throws(Exception::class)
//private fun checkThunder(flags: List<Flag>) {
//    for (flag in flags) {
//        val executor = java.util.concurrent.Executors.newFixedThreadPool(Constant.THREAD_POOL * 2)
//        for (future in executor.invokeAll(flag.getMagnet(), 30, TimeUnit.SECONDS)) flag.getEpisodes()
//            .addAll(future.get())
//        executor.shutdownNow()
//    }
//}
