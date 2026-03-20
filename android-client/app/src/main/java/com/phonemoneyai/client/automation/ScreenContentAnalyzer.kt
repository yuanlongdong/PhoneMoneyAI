package com.phonemoneyai.client.automation

import android.view.accessibility.AccessibilityNodeInfo

internal data class ScreenInsight(
    val title: String,
    val isLive: Boolean,
    val isAd: Boolean,
    val matchedKeywords: List<String>,
    val signature: String,
)

internal object ScreenContentAnalyzer {
    private val liveKeywords = listOf("直播", "直播中", "正在直播", "live")
    private val adKeywords = listOf("广告", "推广", "赞助", "sponsored", "ad", "立即购买", "去购买", "购物车")
    private val ignoredExactTexts = setOf(
        "首页", "朋友", "消息", "我", "同城", "评论", "点赞", "分享", "关注", "搜索", "更多"
    )

    fun analyze(root: AccessibilityNodeInfo?): ScreenInsight {
        val snippets = linkedSetOf<String>()
        collectTexts(root, snippets)
        val normalized = snippets.map { it.trim() }.filter { it.isNotBlank() }
        val lowerCaseSnippets = normalized.map { it.lowercase() }

        val liveMatches = liveKeywords.filter { keyword ->
            lowerCaseSnippets.any { it.contains(keyword.lowercase()) }
        }
        val adMatches = adKeywords.filter { keyword ->
            lowerCaseSnippets.any { it.contains(keyword.lowercase()) }
        }

        val title = chooseTitle(normalized)
        val signature = buildString {
            append(title)
            append('|')
            append(liveMatches.sorted().joinToString(","))
            append('|')
            append(adMatches.sorted().joinToString(","))
        }

        return ScreenInsight(
            title = title,
            isLive = liveMatches.isNotEmpty(),
            isAd = adMatches.isNotEmpty(),
            matchedKeywords = (liveMatches + adMatches).distinct(),
            signature = signature,
        )
    }

    private fun chooseTitle(snippets: List<String>): String {
        return snippets
            .asSequence()
            .filterNot { it in ignoredExactTexts }
            .filterNot { candidate ->
                liveKeywords.any { candidate.contains(it, ignoreCase = true) } ||
                    adKeywords.any { candidate.contains(it, ignoreCase = true) }
            }
            .filter { it.length >= 4 }
            .sortedByDescending { it.length }
            .firstOrNull()
            ?: snippets.firstOrNull { it.length >= 2 }
            ?: "未识别视频标题"
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, sink: MutableSet<String>) {
        if (node == null) return
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(sink::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(sink::add)
        for (index in 0 until node.childCount) {
            collectTexts(node.getChild(index), sink)
        }
    }
}
