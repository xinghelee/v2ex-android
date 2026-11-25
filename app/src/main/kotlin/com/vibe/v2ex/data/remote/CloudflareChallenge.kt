package com.vibe.v2ex.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

internal const val CLOUDFLARE_CHALLENGE_MESSAGE =
    "请求被 Cloudflare 安全验证拦截，当前网络线路可能被限制。登录状态未受影响，请稍后重试或更换线路。"

/**
 * Cloudflare 对低信誉 IP（常见于国内网络、代理线路）返回的挑战页。它和「未登录」完全不是
 * 一回事：如果按普通 403 处理，会被误判成「网页会话已失效」或抛出 JSON 解析错误。
 */
class CloudflareChallengeException : IOException(CLOUDFLARE_CHALLENGE_MESSAGE)

/** Cloudflare 在托管挑战响应上固定带 `cf-mitigated: challenge`。 */
internal fun Response.isCloudflareChallenge(): Boolean =
    header("cf-mitigated")?.trim().equals("challenge", ignoreCase = true)

object CloudflareChallengeInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isCloudflareChallenge()) {
            response.close()
            throw CloudflareChallengeException()
        }
        return response
    }
}
