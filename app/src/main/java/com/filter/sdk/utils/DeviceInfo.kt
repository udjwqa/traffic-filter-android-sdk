package com.filter.sdk.utils

import android.content.Context
import android.opengl.GLES20
import android.os.Build
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext

object DeviceInfo {

    fun getUserAgent(): String {
        return "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; " +
                "${Build.MODEL} Build/${Build.DISPLAY}) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    fun getDeviceModel(): String = Build.MODEL

    fun getDeviceCodename(): String = Build.DEVICE

    fun getBuildProduct(): String = Build.PRODUCT

    fun getOsVersion(): String = "Android ${Build.VERSION.RELEASE}"

    fun getGpuRenderer(): String {
        return try {
            val egl = EGLContext.getEGL() as EGL10
            val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            egl.eglInitialize(display, IntArray(2))

            val configs = arrayOfNulls<EGLConfig>(1)
            val configAttribs = intArrayOf(
                EGL10.EGL_RENDERABLE_TYPE, 4,
                EGL10.EGL_NONE
            )
            egl.eglChooseConfig(display, configAttribs, configs, 1, IntArray(1))

            val contextAttribs = intArrayOf(0x3098, 2, EGL10.EGL_NONE)
            val context = egl.eglCreateContext(
                display, configs[0], EGL10.EGL_NO_CONTEXT, contextAttribs
            )

            val surfaceAttribs = intArrayOf(
                EGL10.EGL_WIDTH, 1, EGL10.EGL_HEIGHT, 1, EGL10.EGL_NONE
            )
            val surface = egl.eglCreatePbufferSurface(display, configs[0], surfaceAttribs)
            egl.eglMakeCurrent(display, surface, surface, context)

            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""

            egl.eglMakeCurrent(
                display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT
            )
            egl.eglDestroySurface(display, surface)
            egl.eglDestroyContext(display, context)
            egl.eglTerminate(display)

            renderer
        } catch (e: Exception) {
            ""
        }
    }

    fun getAcceptLanguage(context: Context): String {
        val locale = context.resources.configuration.locales[0]
        return "${locale.language}-${locale.country},${locale.language};q=0.9,en;q=0.8"
    }
}
