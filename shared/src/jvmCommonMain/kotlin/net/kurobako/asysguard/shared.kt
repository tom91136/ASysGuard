package net.kurobako.asysguard

import okhttp3.Interceptor
import okhttp3.ResponseBody.Companion.toResponseBody

fun scale(
  x: Float,
  xMin: Float,
  xMax: Float,
  outMin: Float,
  outMax: Float,
): Float = (outMax - outMin) * (x - xMin) / (xMax - xMin) + outMin

inline fun <reified T> transpose(xs: List<List<T>>): List<List<T>> {
  if (xs.isEmpty()) return emptyList()
  val cols = xs[0].size
  val rows = xs.size
  return List(cols) { j ->
    List(rows) { i ->
      xs[i][j]
    }
  }
}

val SafeInterceptor =
  object : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
      val request = chain.request()
      try {
        val response = chain.proceed(request)
        return response
          .newBuilder()
          .body(response.body!!.string().toResponseBody(response.body?.contentType()))
          .build()
      } catch (e: Exception) {
        return okhttp3.Response
          .Builder()
          .request(request)
          .protocol(okhttp3.Protocol.HTTP_1_1)
          .code(500)
          .message(e.message.orEmpty())
          .body(e.toString().toResponseBody(null))
          .build()
      }
    }
  }
