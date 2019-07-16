package com.dmitriy.android.serialportturkish.old_version


import android.support.annotation.CallSuper
import android.support.v7.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

open class CoroutineAppCompatActivity: AppCompatActivity(), CoroutineScope {
    private val activityJob = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + activityJob

    @CallSuper
    override fun onDestroy() {
        activityJob.cancel()
        super.onDestroy()
    }
}