package com.blvietsub

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class BLVietsubPlugin: Plugin() {
    override fun load(context: Context) {
        // Đăng ký Provider chính BLVietsub (Toàn bộ Extractor SSPlay, OK.ru, VK, Dailymotion được Provider nạp và trích xuất trực tiếp)
        registerMainAPI(BLVietsubProvider())
    }
}

