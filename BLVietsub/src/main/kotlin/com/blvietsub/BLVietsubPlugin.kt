package com.blvietsub

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class BLVietsubPlugin: Plugin() {
    override fun load(context: Context) {
        // Đăng ký Provider BLVietsub
        registerMainAPI(BLVietsubProvider())
        // Đăng ký Extractor SSPlay (1080p FHD)
        registerExtractorAPI(SSPlayExtractor())
        // Đăng ký Extractor OK.ru (Server DL 1080p FHD)
        registerExtractorAPI(OkRuExtractor())
        // Đăng ký Extractor Dailymotion (Server DL 1080p FHD)
        registerExtractorAPI(DailymotionExtractor())
    }
}
