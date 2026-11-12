package com.tkno.scan.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.View
import androidx.core.os.ConfigurationCompat
import androidx.core.text.TextUtilsCompat
import com.tkno.scan.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class BuiltInLanguage(
    val displayName: String,
    val tag: String,
    val locale: Locale
)

data class RemoteLanguageItem(
    val code: String,
    val name: String,
    val englishName: String,
    val direction: String = "ltr",
    val file: String,
    val stringCount: Int = 0
)

data class CustomLanguage(
    val code: String,
    val name: String,
    val englishName: String,
    val direction: String = "ltr",
    val stringCount: Int = 0,
    val strings: Map<String, String> = emptyMap()
)

object LanguageManager {
    // Official GitHub raw base URL for Scan language packs
    var GITHUB_BASE_URL: String = "https://raw.githubusercontent.com/hamzabellouch/language/main/Scan"

    val builtInLanguages: List<BuiltInLanguage> = listOf(
        BuiltInLanguage("English", "en", Locale.ENGLISH),
        BuiltInLanguage("العربية", "ar", Locale.forLanguageTag("ar")),
        BuiltInLanguage("Français", "fr", Locale.FRENCH)
    )

    fun isBuiltInLanguage(tag: String): Boolean {
        if (tag == "system") return true
        return builtInLanguages.any { it.tag.equals(tag, ignoreCase = true) }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getLanguagesDir(context: Context): File {
        val dir = File(context.filesDir, "custom_languages")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getInstalledCustomLanguages(context: Context): List<CustomLanguage> {
        return try {
            val dir = getLanguagesDir(context)
            val files = dir.listFiles { file -> file.extension == "json" } ?: return emptyList()
            val list = mutableListOf<CustomLanguage>()
            for (file in files) {
                try {
                    val json = JSONObject(file.readText(Charsets.UTF_8))
                    val code = json.optString("code", file.nameWithoutExtension)
                    val name = json.optString("name", code)
                    val englishName = json.optString("english_name", name)
                    val direction = json.optString("direction", "ltr")
                    val stringsObj = json.optJSONObject("strings")
                    val count = stringsObj?.length() ?: 0
                    list.add(
                        CustomLanguage(
                            code = code,
                            name = name,
                            englishName = englishName,
                            direction = direction,
                            stringCount = count
                        )
                    )
                } catch (_: Exception) {}
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadCustomLanguage(context: Context, code: String): CustomLanguage? {
        val dir = getLanguagesDir(context)
        val file = File(dir, "$code.json")
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val langCode = json.optString("code", code)
            val name = json.optString("name", langCode)
            val englishName = json.optString("english_name", name)
            val direction = json.optString("direction", "ltr")
            val stringsMap = mutableMapOf<String, String>()
            val stringsObj = json.optJSONObject("strings")
            if (stringsObj != null) {
                val keys = stringsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    stringsMap[key] = stringsObj.optString(key, "")
                }
            }
            CustomLanguage(
                code = langCode,
                name = name,
                englishName = englishName,
                direction = direction,
                stringCount = stringsMap.size,
                strings = stringsMap
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveCustomLanguage(context: Context, jsonString: String): CustomLanguage? {
        return try {
            val json = JSONObject(jsonString)
            val code = json.getString("code")
            val name = json.optString("name", code)
            val englishName = json.optString("english_name", name)
            val direction = json.optString("direction", "ltr")
            val stringsMap = mutableMapOf<String, String>()
            val stringsObj = json.optJSONObject("strings")
            if (stringsObj != null) {
                val keys = stringsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    stringsMap[key] = stringsObj.optString(key, "")
                }
            }

            val dir = getLanguagesDir(context)
            val file = File(dir, "$code.json")
            file.writeText(jsonString, Charsets.UTF_8)

            CustomLanguage(
                code = code,
                name = name,
                englishName = englishName,
                direction = direction,
                stringCount = stringsMap.size,
                strings = stringsMap
            )
        } catch (_: Exception) {
            null
        }
    }

    fun deleteCustomLanguage(context: Context, code: String): Boolean {
        val dir = getLanguagesDir(context)
        val file = File(dir, "$code.json")
        val deleted = if (file.exists()) file.delete() else false

        // If active language was this deleted language, reset to system
        val prefs = PreferenceUtil.getPrefs(context)
        val currentLang = prefs.getString("app_language", "system") ?: "system"
        if (currentLang == code) {
            prefs.edit().putString("app_language", "system").commit()
        }
        return deleted
    }

    suspend fun fetchRemoteLanguages(repoBaseUrl: String? = null): Result<List<RemoteLanguageItem>> = withContext(Dispatchers.IO) {
        val base = (repoBaseUrl ?: GITHUB_BASE_URL).trimEnd('/')
        val url = "$base/languages_index.json"
        try {
            val request = Request.Builder()
                .url(url)
                .build()
            val response = HttpClient.client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}"))
            }
            val body = response.body.string()
            val root = JSONObject(body)
            val array = root.getJSONArray("languages")
            val resultList = mutableListOf<RemoteLanguageItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val code = obj.getString("code")
                val name = obj.getString("name")
                val englishName = obj.optString("english_name", name)
                val direction = obj.optString("direction", "ltr")
                val file = obj.getString("file")
                val count = obj.optInt("string_count", 0)
                resultList.add(
                    RemoteLanguageItem(
                        code = code,
                        name = name,
                        englishName = englishName,
                        direction = direction,
                        file = file,
                        stringCount = count
                    )
                )
            }
            Result.success(resultList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadRemoteLanguage(
        context: Context,
        item: RemoteLanguageItem,
        repoBaseUrl: String? = null
    ): Result<CustomLanguage> = withContext(Dispatchers.IO) {
        val base = (repoBaseUrl ?: GITHUB_BASE_URL).trimEnd('/')
        val relativeFile = item.file.trimStart('/')
        val url = "$base/$relativeFile"
        try {
            val request = Request.Builder()
                .url(url)
                .build()
            val response = HttpClient.client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}"))
            }
            val body = response.body.string()
            val customLang = saveCustomLanguage(context, body)
                ?: return@withContext Result.failure(Exception("Invalid language format"))
            Result.success(customLang)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSystemLocale(): Locale {
        val systemConfig = Resources.getSystem().configuration
        return ConfigurationCompat.getLocales(systemConfig).get(0) ?: Locale.getDefault()
    }

    fun getLocaleForTag(tag: String): Locale {
        val builtIn = builtInLanguages.find { it.tag.equals(tag, ignoreCase = true) }
        if (builtIn != null) {
            return builtIn.locale
        }
        val cleanTag = when {
            tag.startsWith("b+") -> tag.removePrefix("b+").replace('+', '-')
            else -> tag
        }
        return Locale.forLanguageTag(cleanTag)
    }

    fun resolveEffectiveLanguageTag(context: Context, preferenceTag: String): String {
        return try {
            if (preferenceTag != "system") {
                val builtIn = builtInLanguages.find { it.tag.equals(preferenceTag, ignoreCase = true) }
                if (builtIn != null) {
                    return builtIn.tag
                }
                val installed = getInstalledCustomLanguages(context)
                val custom = installed.find { it.code.equals(preferenceTag, ignoreCase = true) }
                if (custom != null) {
                    return custom.code
                }
                return "en"
            }

            // preferenceTag == "system" (Follow system)
            val sysLocale = getSystemLocale()
            val sysLang = sysLocale.language.lowercase(Locale.ROOT)
            val sysTag = sysLocale.toLanguageTag().lowercase(Locale.ROOT)

            // 1. Built-in languages check (en, ar, fr)
            val matchedBuiltIn = builtInLanguages.find {
                it.tag.equals(sysLang, ignoreCase = true) || it.tag.equals(sysTag, ignoreCase = true)
            }
            if (matchedBuiltIn != null) {
                return matchedBuiltIn.tag
            }

            // 2. Custom downloaded languages check
            val installed = getInstalledCustomLanguages(context)
            val matchedCustom = installed.find { item ->
                val cleanCode = item.code.removePrefix("b+").replace('+', '-').lowercase(Locale.ROOT)
                cleanCode.equals(sysTag, ignoreCase = true) ||
                    cleanCode.equals(sysLang, ignoreCase = true) ||
                    cleanCode.substringBefore('-').equals(sysLang, ignoreCase = true) ||
                    item.code.equals(sysLang, ignoreCase = true)
            }
            if (matchedCustom != null) {
                return matchedCustom.code
            }

            // 3. Fallback to English
            "en"
        } catch (_: Exception) {
            "en"
        }
    }

    fun isRtl(context: Context, effectiveTag: String): Boolean {
        val custom = loadCustomLanguage(context, effectiveTag)
        if (custom != null) {
            return custom.direction.equals("rtl", ignoreCase = true)
        }
        val locale = getLocaleForTag(effectiveTag)
        return TextUtilsCompat.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
    }

    fun getDisplayName(context: Context, tag: String): String {
        if (tag == "system") {
            val effectiveTag = resolveEffectiveLanguageTag(context, "system")
            val effectiveName = when (effectiveTag) {
                "en" -> "English"
                "ar" -> "العربية"
                "fr" -> "Français"
                else -> {
                    val custom = loadCustomLanguage(context, effectiveTag)
                    custom?.name ?: effectiveTag
                }
            }
            return "${context.getString(R.string.follow_system)} ($effectiveName)"
        }
        val builtIn = builtInLanguages.find { it.tag.equals(tag, ignoreCase = true) }
        if (builtIn != null) {
            return builtIn.displayName
        }
        val custom = loadCustomLanguage(context, tag)
        if (custom != null) {
            return custom.name
        }
        return tag
    }

    fun wrapContext(baseContext: Context, preferenceTag: String): Context {
        return try {
            val effectiveTag = resolveEffectiveLanguageTag(baseContext, preferenceTag)

            val builtIn = builtInLanguages.find { it.tag.equals(effectiveTag, ignoreCase = true) }
            if (builtIn != null) {
                val config = Configuration(baseContext.resources.configuration)
                config.setLocale(builtIn.locale)
                config.setLayoutDirection(builtIn.locale)
                val localizedConfigContext = baseContext.createConfigurationContext(config)
                return object : ContextWrapper(baseContext) {
                    override fun getResources(): Resources = localizedConfigContext.resources
                    override fun getAssets(): android.content.res.AssetManager = localizedConfigContext.assets
                }
            }

            // Custom downloaded language
            val custom = loadCustomLanguage(baseContext, effectiveTag)
            if (custom != null) {
                val cleanTag = when {
                    custom.code.startsWith("b+") -> custom.code.removePrefix("b+").replace('+', '-')
                    else -> custom.code
                }
                val locale = Locale.forLanguageTag(cleanTag)
                val config = Configuration(baseContext.resources.configuration)
                config.setLocale(locale)
                val layoutLocale = if (custom.direction.equals("rtl", ignoreCase = true)) Locale.forLanguageTag("ar") else locale
                config.setLayoutDirection(layoutLocale)
                val localizedConfigContext = baseContext.createConfigurationContext(config)
                val dynamicResources = DynamicResources(localizedConfigContext.resources, custom.strings)
                return object : ContextWrapper(baseContext) {
                    override fun getResources(): Resources = dynamicResources
                    override fun getAssets(): android.content.res.AssetManager = localizedConfigContext.assets
                }
            }

            baseContext
        } catch (_: Exception) {
            baseContext
        }
    }
}

@Suppress("DEPRECATION")
class DynamicResources(
    private val baseResources: Resources,
    private val customStrings: Map<String, String>
) : Resources(baseResources.assets, baseResources.displayMetrics, baseResources.configuration) {

    override fun getText(id: Int): CharSequence {
        try {
            val entryName = getResourceEntryName(id)
            val custom = customStrings[entryName]
            if (!custom.isNullOrEmpty()) {
                return custom
            }
        } catch (_: Exception) {}
        return baseResources.getText(id)
    }

    override fun getText(id: Int, def: CharSequence?): CharSequence {
        try {
            val entryName = getResourceEntryName(id)
            val custom = customStrings[entryName]
            if (!custom.isNullOrEmpty()) {
                return custom
            }
        } catch (_: Exception) {}
        return baseResources.getText(id, def)
    }

    override fun getString(id: Int): String {
        try {
            val entryName = getResourceEntryName(id)
            val custom = customStrings[entryName]
            if (!custom.isNullOrEmpty()) {
                return custom
            }
        } catch (_: Exception) {}
        return baseResources.getString(id)
    }

    override fun getString(id: Int, vararg formatArgs: Any?): String {
        try {
            val entryName = getResourceEntryName(id)
            val custom = customStrings[entryName]
            if (!custom.isNullOrEmpty()) {
                return String.format(custom, *formatArgs)
            }
        } catch (_: Exception) {}
        return baseResources.getString(id, *formatArgs)
    }
}
