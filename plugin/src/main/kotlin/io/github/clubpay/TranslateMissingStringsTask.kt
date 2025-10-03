package io.github.clubpay

import org.gradle.api.GradleException
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Internal
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import com.google.gson.Gson

open class TranslateMissingStringsTask : DefaultTask() {
    
    @Internal
    var extension: QlubAutoTranslateExtension? = null
    
    private companion object {
        const val BATCH_SIZE = 50
        const val API_CONNECT_TIMEOUT_SECONDS = 300
        const val API_MAX_TIME_SECONDS = 600
        const val OUTPUT_SEPARATOR_LENGTH = 60
        
        val SUPPORTED_LANGUAGES = mapOf(
            "de" to "German",
            "fr" to "French",
            "es" to "Spanish",
            "tr" to "Turkish",
            "zh-rHK" to "Traditional Chinese (Hong Kong)",
            "zh-rSG" to "Simplified Chinese (Singapore)",
            "ko" to "Korean",
            "ja" to "Japanese",
            "it" to "Italian",
            "pt" to "Portuguese",
            "ru" to "Russian",
            "ar" to "Arabic"
        )
    }
    
    private enum class LogLevel {
        INFO,
        VERBOSE,
        ERROR
    }
    
    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        val isVerbose = extension?.verbose?.get() == true
        when (level) {
            LogLevel.INFO -> println(message)
            LogLevel.VERBOSE -> if (isVerbose) println(message)
            LogLevel.ERROR -> println("❌ ERROR: $message")
        }
    }
    
    /**
     * Normalizes a JSON string that might be double-encoded.
     * Handles cases where JSON is returned as a string containing JSON.
     */
    private fun normalizeJsonString(jsonString: String): String {
        val gson = Gson()
        return try {
            val any = gson.fromJson(jsonString, Any::class.java)
            when (any) {
                is Map<*, *> -> jsonString
                is String -> {
                    val innerAny = try { 
                        gson.fromJson(any, Any::class.java) 
                    } catch (_: Exception) { null }
                    if (innerAny is Map<*, *>) any else jsonString
                }
                else -> jsonString
            }
        } catch (_: Exception) { jsonString }
    }
    
    /**
     * Parses JSON string into specified type with consistent error handling.
     * @param jsonString The JSON string to parse
     * @param errorMessage Error message prefix for exceptions
     * @param logContent Whether to log the full JSON content on error
     */
    private inline fun <reified T> parseJson(
        jsonString: String, 
        errorMessage: String,
        logContent: Boolean = false
    ): T {
        val gson = Gson()
        return try {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(jsonString, Map::class.java) as T
        } catch (e: Exception) {
            if (logContent) {
                log("$errorMessage. Full body:", LogLevel.ERROR)
                log(jsonString, LogLevel.ERROR)
            } else {
                log("$errorMessage: ${e.message}", LogLevel.ERROR)
            }
            throw GradleException("$errorMessage: ${e.message}")
        }
    }
    
    /**
     * Validates if a string is valid JSON without throwing exceptions.
     */
    private fun isValidJson(jsonString: String): Boolean {
        return try {
            Gson().fromJson(jsonString, Map::class.java)
            true
        } catch (_: Exception) { false }
    }
    
    /**
     * Extracts top-level keys from a JSON object.
     * Returns empty list if parsing fails.
     */
    private fun extractJsonKeys(jsonString: String): List<String> {
        return try {
            val gson = Gson()
            @Suppress("UNCHECKED_CAST")
            (gson.fromJson(jsonString, Map::class.java) as Map<String, Any?>)
                .keys.map { it as String }
        } catch (_: Exception) { emptyList() }
    }
    
    /**
     * Extracts the first complete JSON object from a string.
     * Handles cases where AI returns extra text around the JSON.
     */
    private fun extractFirstJsonObject(input: String): String? {
        var depth = 0
        var started = false
        var startIdx = -1
        
        for (i in input.indices) {
            val c = input[i]
            if (c == '{') {
                if (!started) {
                    started = true
                    startIdx = i
                }
                depth++
            } else if (c == '}') {
                if (started) {
                    depth--
                    if (depth == 0) {
                        return input.substring(startIdx, i + 1)
                    }
                }
            }
        }
        return null
    }
    
    @TaskAction
    fun translateMissingStrings() {
        translateMissingStringsLogic(project)
    }
    
    private fun translateMissingStringsLogic(rootProject: org.gradle.api.Project) {
        log("\n> Task :app:translateMissingStrings")
        log("\nAutomated batch translation of missing strings...")

        val langsProp = (rootProject.findProperty("langs") as String?)
        val singleLangProp = (rootProject.findProperty("lang") as String?)
        val targetLanguages = when {
            !langsProp.isNullOrBlank() -> langsProp.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            !singleLangProp.isNullOrBlank() -> setOf(singleLangProp.trim())
            else -> throw GradleException("Missing required parameter. Usage: -Plangs=tr,de,fr or -Plang=de")
        }

        log("Target languages: ${targetLanguages.joinToString(", ")}")

        val apiKey = extension?.apiKey?.get() ?: ""
        if (apiKey.isBlank()) {
            throw GradleException("AI_API_KEY not configured in qlubAutoTranslate extension. Please add: qlubAutoTranslate { apiKey = \"your_openai_api_key\" }")
        }

        val moduleResDirs = mutableMapOf<String, File>()

        fun discoverModules(dir: File, modulePath: String = "") {
            if (!dir.exists() || !dir.isDirectory) return

            val resDir = File(dir, "src/main/res")
            if (resDir.exists()) {
                val moduleKey = if (modulePath.isEmpty()) "app" else modulePath
                moduleResDirs[moduleKey] = resDir
            }

            dir.listFiles()?.forEach { subDir ->
                if (subDir.isDirectory && !subDir.name.startsWith(".") && subDir.name != "build") {
                    val newModulePath = if (modulePath.isEmpty()) subDir.name else "$modulePath/${subDir.name}"
                    discoverModules(subDir, newModulePath)
                }
            }
        }

        log("Discovering modules with resources...", LogLevel.VERBOSE)
        discoverModules(rootProject.projectDir)
        log("Found ${moduleResDirs.size} modules with res directories")

        val payload = buildTranslationPayload(targetLanguages.toList(), moduleResDirs)

        if (payload.isEmpty()) {
            log("\nNo missing translations detected for requested languages. Nothing to translate.")
            return
        }

        val gson = Gson()
        val mergedTranslations = mutableMapOf<String, MutableMap<String, String>>()

        fun <K, V> Map<K, V>.chunkedMap(chunkSize: Int): List<Map<K, V>> {
            if (this.isEmpty()) return emptyList()
            val entriesList = this.entries.toList()
            val result = mutableListOf<Map<K, V>>()
            var index = 0
            while (index < entriesList.size) {
                val nextIndex = kotlin.math.min(index + chunkSize, entriesList.size)
                result.add(entriesList.subList(index, nextIndex).associate { it.key to it.value })
                index = nextIndex
            }
            return result
        }

        log("\nTranslating in batches of $BATCH_SIZE keys per language...")
        payload.toSortedMap().forEach { (lang, langMap) ->
            val batches = langMap.chunkedMap(BATCH_SIZE)
            log("[$lang] ${batches.size} batch(es) to translate (max $BATCH_SIZE per batch)")
            batches.forEachIndexed { batchIndex, batchMap ->
                val batchJson = gson.toJson(mapOf(lang to batchMap))
                log("[$lang] → Sending batch ${batchIndex + 1}/${batches.size} with ${batchMap.size} keys")
                val responseJson = batchTranslateText(batchJson, apiKey)

                val resp = parseJson<Map<String, Map<String, String>>>(
                    responseJson,
                    "Failed to parse batch response for '$lang' batch ${batchIndex + 1}"
                )
                resp.forEach { (respLang, respMap) ->
                    val target = mergedTranslations.getOrPut(respLang) { mutableMapOf() }
                    target.putAll(respMap)
                }
            }
        }

        val mergedJson = gson.toJson(mergedTranslations as Map<String, Map<String, String>>)
        applyBatchTranslations(mergedJson, moduleResDirs)
    }
    
    private fun parseStringKeys(xmlFile: File): List<String> {
        val keys = mutableListOf<String>()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xmlFile)

            document.documentElement.normalize()

            val stringElements = document.getElementsByTagName("string")

            for (i in 0 until stringElements.length) {
                val element = stringElements.item(i) as Element

                val nameAttr = element.getAttribute("name")
                if (nameAttr.isNotEmpty()) {
                    val translatableAttr = element.getAttribute("translatable")
                    val isTranslatable = translatableAttr.isEmpty() || translatableAttr.lowercase() != "false"

                    if (isTranslatable) {
                        keys.add(nameAttr)
                    }
                }
            }

        } catch (e: Exception) {
            log("Error parsing ${xmlFile.name}: ${e.message}", LogLevel.ERROR)
        }

        return keys
    }
    
    private fun getSourceStringValue(xmlFile: File, key: String): String {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xmlFile)

            document.documentElement.normalize()

            val stringElements = document.getElementsByTagName("string")

            for (i in 0 until stringElements.length) {
                val element = stringElements.item(i) as Element
                val nameAttr = element.getAttribute("name")

                if (nameAttr == key) {
                    return element.textContent ?: ""
                }
            }

        } catch (e: Exception) {
            log("Error extracting string value from ${xmlFile.name}: ${e.message}", LogLevel.ERROR)
        }

        return ""
    }
    
    private fun buildTranslationPayload(targetLanguages: List<String>, moduleResDirs: Map<String, File>): Map<String, MutableMap<String, String>> {
        log("\nCollecting missing keys across modules and languages...", LogLevel.VERBOSE)

        val payload = mutableMapOf<String, MutableMap<String, String>>()

        var totalMissing = 0

        moduleResDirs.toSortedMap().forEach { (module, resDir) ->
            val valuesDir = File(resDir, "values")
            val defaultFile = File(valuesDir, "strings.xml")
            if (!defaultFile.exists()) return@forEach

            val defaultKeys = parseStringKeys(defaultFile).toSet()
            log("[$module] default strings: ${defaultKeys.size}", LogLevel.VERBOSE)

            targetLanguages.forEach { lang ->
                val targetDir = File(resDir, "values-$lang")
                val targetFile = File(targetDir, "strings.xml")
                val existingKeys = if (targetFile.exists()) parseStringKeys(targetFile).toSet() else emptySet()

                val missing = defaultKeys - existingKeys
                if (missing.isNotEmpty()) {
                    log("[$module][$lang] missing ${missing.size} keys", LogLevel.VERBOSE)
                    totalMissing += missing.size

                    val langMap = payload.getOrPut(lang) { mutableMapOf() }
                    missing.forEach { key ->
                        if (!langMap.containsKey(key)) {
                            val source = getSourceStringValue(defaultFile, key)
                            if (source.isNotEmpty()) {
                                langMap[key] = source
                            }
                        }
                    }
                } else {
                    log("[$module][$lang] ✓ no missing keys", LogLevel.VERBOSE)
                }
            }
        }

        if (payload.isEmpty()) {
            log("No missing translations found.")
            return emptyMap()
        }

        log("\nTotal missing keys to translate: $totalMissing")
        return payload
    }
    
    /**
     * Calls OpenAI API with the given request body.
     * Handles file creation, curl execution, and error handling.
     */
    private fun callOpenAIApi(requestBody: String, apiKey: String): String {
        val tempFile = File.createTempFile("openai_batch_request", ".json")
        tempFile.writeText(requestBody)
        
        try {
            val process = ProcessBuilder(
                "curl",
                "-X", "POST",
                "https://api.openai.com/v1/chat/completions",
                "-H", "Content-Type: application/json",
                "-H", "Authorization: Bearer $apiKey",
                "--data-binary", "@${tempFile.absolutePath}",
                "--connect-timeout", API_CONNECT_TIMEOUT_SECONDS.toString(),
                "--max-time", API_MAX_TIME_SECONDS.toString(),
                "--silent",
                "--show-error"
            ).start()
            
            val response = process.inputStream.bufferedReader().use { it.readText() }
            val errorOutput = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            
            if (exitCode != 0) {
                log("Curl failed with exit code: $exitCode", LogLevel.ERROR)
                if (errorOutput.isNotEmpty()) log("Error output: $errorOutput", LogLevel.ERROR)
                if (response.isNotEmpty()) log("Response: $response", LogLevel.ERROR)
                throw GradleException("API request failed")
            }
            
            if (response.isBlank()) {
                throw GradleException("Empty response from API")
            }
            
            return response
        } finally {
            tempFile.delete()
        }
    }
    
    /**
     * Extracts and validates translation JSON from API response.
     * Handles code fences, JSON extraction, normalization and validation.
     */
    private fun extractTranslationContent(response: String): String {
        val root = parseJson<Map<*, *>>(
            response,
            "Failed to parse API response JSON",
            logContent = true
        )
        
        val choices = root["choices"] as? List<*>
        val firstChoice = choices?.firstOrNull() as? Map<*, *>
        val message = firstChoice?.get("message") as? Map<*, *>
        var content = message?.get("content") as? String
            ?: throw GradleException("Could not extract assistant content from API response").also {
                log("Unexpected API response missing content. Full body:", LogLevel.ERROR)
                log(response, LogLevel.ERROR)
            }
        
        if (content.startsWith("```")) {
            content = content.replace("```json", "").replace("```JSON", "").replace("```", "").trim()
        }
        
        extractFirstJsonObject(content)?.let { content = it }
        
        val normalized = normalizeJsonString(content)
        
        if (!isValidJson(normalized)) {
            log("Assistant content is not valid JSON object. Content:\n$normalized", LogLevel.ERROR)
            throw GradleException("Assistant did not return valid JSON object")
        }
        
        log("Received batch translations JSON (${normalized.length} chars)", LogLevel.VERBOSE)
        return normalized
    }
    
    private fun batchTranslateText(jsonInput: String, apiKey: String): String {
        log("\nSending batch translation request...", LogLevel.VERBOSE)

        val topLevelLangs = extractJsonKeys(jsonInput)
        val langHints = if (topLevelLangs.isNotEmpty()) {
            topLevelLangs.joinToString(", ") { code -> 
                "$code=" + (SUPPORTED_LANGUAGES[code] ?: code) 
            }
        } else ""

        log("LangHints: $langHints", LogLevel.VERBOSE)

        val systemRules = """
        You are a highly accurate translation engine for Android string resources.
        Your ONLY task is to return valid JSON that strictly preserves the input structure.
        Rules:
        - Preserve all placeholders exactly: %s, %d, %1${'$'}s, %2${'$'}d, etc. Do NOT invent any new placeholders.
        - Preserve all HTML tags and valid escapes (\n, \t, \r, \\) exactly as they appear.
        - Do NOT modify technical IDs or tokens (orderID, tableID, partyID, diningSessionID, reference, txnID, token).
        - Do NOT translate brand/provider names: Stripe, Adyen, NearPay, N-Genius, Paymob, Geidea, Mashreq, Tyro.
        - NEVER add explanations, comments, code fences, or extra text.
        - Strictly match each top-level key to its target language and locale; never use a different language than requested.
        - If you see ' this character always change with \'
        - If you see & this character always change with &amp;
        """.trimIndent()

        val appContext = extension?.appContext?.get() ?: "A mobile application"
        val isVerboseMode = extension?.verbose?.get() == true
        val model = extension?.model?.get() ?: "gpt-5-nano"
        
        log("Using AI model: $model")
        
        val userPrompt = """
        Translate the following Android app strings to $langHints.

        App context:
        $appContext

        Tone and Style Guide:
        - Translations must be natural and fluent, as if written by a native speaker for a modern mobile app.
        - Avoid overly literal or "robotic" translations. Prioritize user-friendliness and clarity.
        - Use terminology commonly found in modern $langHints apps.
        - If a term has multiple translations, prefer the one commonly used in the target locale.

        Critical formatting rules:
        1. DO NOT translate or alter any placeholders: %s, %d, %1${'$'}s, %2${'$'}d, etc.
        2. Preserve HTML tags and their positions: <b>, <i>, <u>, <font color="...">.
        3. Preserve special characters and escapes (\n, \t, \r, etc.).
        4. Do NOT change numbers, currency symbols, or date/time formats; translate words only.
        5. Keep existing capitalization where meaningful (titles/buttons).
        6. If you see ' this character always change with \'
        7. If you see & this character always change with &amp;

        Output format rules:
        - Return ONLY valid JSON with the same structure as input.
        - Keys and nested structure must remain exactly as input.
        - Output JSON only; no explanations, quotes, or extra text.

        INPUT JSON:
        $jsonInput
        """.trimIndent()

        if (isVerboseMode) {
            println("\n" + "=".repeat(80))
            println("📝 VERBOSE MODE: Full translation prompt")
            println("=".repeat(80))
            println(userPrompt)
            println("=".repeat(80))
            println("📝 VERBOSE MODE: End of prompt")
            println("=".repeat(80) + "\n")
        }

        val gson = Gson()
        val requestBody = gson.toJson(
            mapOf(
                "model" to model,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to systemRules),
                    mapOf("role" to "user", "content" to userPrompt)
                )
            )
        )

        val response = callOpenAIApi(requestBody, apiKey)
        return extractTranslationContent(response)
    }
    
    private fun applyBatchTranslations(jsonOutput: String, moduleResDirs: Map<String, File>) {
        log("\nApplying batch translations to modules...", LogLevel.VERBOSE)

        val normalized = normalizeJsonString(jsonOutput)

        val translations = parseJson<Map<String, Map<String, String>>>(
            normalized,
            "Failed to parse JSON response into expected structure"
        )

        var addedCount = 0
        var modulesTouched = 0

        moduleResDirs.toSortedMap().forEach { (module, resDir) ->
            val valuesDir = File(resDir, "values")
            val defaultFile = File(valuesDir, "strings.xml")
            if (!defaultFile.exists()) return@forEach

            val defaultKeys = parseStringKeys(defaultFile).toSet()

            translations.forEach { (lang, langMap) ->
                val targetDir = File(resDir, "values-$lang")
                val targetFile = File(targetDir, "strings.xml")

                if (!targetDir.exists()) {
                    log("[$module][$lang] 📁 Creating directory: ${targetDir.absolutePath}", LogLevel.VERBOSE)
                    targetDir.mkdirs()
                }
                if (!targetFile.exists()) {
                    log("[$module][$lang] 📄 Creating strings.xml file: ${targetFile.absolutePath}", LogLevel.VERBOSE)
                    createEmptyStringsXmlFile(targetFile)
                }

                val existingKeys = parseStringKeys(targetFile).toSet()
                val candidateKeys = langMap.keys.filter { it in defaultKeys && it !in existingKeys }

                if (candidateKeys.isEmpty()) {
                    log("[$module][$lang] ✓ no new keys to add", LogLevel.VERBOSE)
                    return@forEach
                }

                log("[$module][$lang] adding ${candidateKeys.size} translations")
                modulesTouched++

                candidateKeys.sorted().forEach { key ->
                    val value = langMap[key]
                    if (!value.isNullOrBlank()) {
                        try {
                            addStringToXmlFile(targetFile, key, value)
                            addedCount++
                        } catch (e: Exception) {
                            log("[$module][$lang] Failed to add '$key': ${e.message}", LogLevel.ERROR)
                        }
                    }
                }
            }
        }

        log("\n" + "=".repeat(OUTPUT_SEPARATOR_LENGTH))
        log("✅ Batch translation applied")
        log("Modules touched: $modulesTouched")
        log("Total translations added: $addedCount")
        log("=".repeat(OUTPUT_SEPARATOR_LENGTH))
    }
    
    private fun createEmptyStringsXmlFile(xmlFile: File) {
        try {
            val emptyStringsContent = """<?xml version="1.0" encoding="utf-8"?>
<resources>
</resources>"""

            xmlFile.writeText(emptyStringsContent)
            log("Successfully created empty strings.xml file: ${xmlFile.absolutePath}", LogLevel.VERBOSE)

        } catch (e: Exception) {
            log("Failed to create empty strings.xml file: ${e.message}", LogLevel.ERROR)
            throw e
        }
    }
    
    private fun addStringToXmlFile(xmlFile: File, key: String, value: String) {
        log("Appending to file: ${xmlFile.absolutePath}", LogLevel.VERBOSE)
        log("Adding: <string name=\"$key\">$value</string>", LogLevel.VERBOSE)

        try {
            if (!xmlFile.exists()) {
                throw Exception("Target XML file does not exist: ${xmlFile.absolutePath}")
            }

            val existingContent = xmlFile.readText()

            fun sanitizeAndroidStringValue(input: String): String {
                val ampFixed = input.replace(
                    Regex("&(?!amp;|lt;|gt;|quot;|apos;|#[0-9]+;|#x[0-9A-Fa-f]+;)")
                    , "&amp;")
                val apostropheEscaped = ampFixed.replace(Regex("(?<!\\\\)\'"), "\\\\'")
                return apostropheEscaped
            }

            val safeValue = sanitizeAndroidStringValue(value)

            val newStringEntry = "    <string name=\"$key\">$safeValue</string>"

            val closingTagIndex = existingContent.lastIndexOf("</resources>")

            if (closingTagIndex == -1) {
                throw Exception("Could not find closing </resources> tag in XML file")
            }

            val keyPattern = """<string\s+name\s*=\s*["']$key["']""".toRegex()
            if (keyPattern.containsMatchIn(existingContent)) {
                log("Warning: Key '$key' already exists in the file. Skipping...", LogLevel.VERBOSE)
                return
            }

            val beforeClosingTag = existingContent.substring(0, closingTagIndex)
            val afterClosingTag = existingContent.substring(closingTagIndex)

            val modifiedContent = beforeClosingTag + newStringEntry + "\n" + afterClosingTag

            xmlFile.writeText(modifiedContent)

            log("Successfully added string to ${xmlFile.name}", LogLevel.VERBOSE)

        } catch (e: Exception) {
            log("Failed to add string to XML file: ${e.message}", LogLevel.ERROR)
            throw e
        }
    }
}