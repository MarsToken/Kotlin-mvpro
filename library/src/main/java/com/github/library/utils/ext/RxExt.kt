/*
 * Copyright (C) 2017 Ricky.yao https://github.com/vihuela
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 */

package com.github.library.utils.ext

import android.app.Activity
import com.blankj.utilcode.util.ToastUtils
import com.ricky.mvp_core.base.BasePresenter
import com.tbruyelle.rxpermissions2.RxPermissions
import com.trello.rxlifecycle2.LifecycleProvider
import com.trello.rxlifecycle2.android.ActivityEvent
import com.trello.rxlifecycle2.android.FragmentEvent
import com.trello.rxlifecycle2.components.support.RxAppCompatActivity
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import com.trello.rxlifecycle2.kotlin.bindUntilEvent
import github.library.parser.ExceptionParseMgr
import github.library.utils.Error
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

//net error parse
fun Throwable.parse(iHandler: (error: Error, message: String) -> Unit) {
    ExceptionParseMgr.Instance.parseException(this, iHandler)
}

//bind BehaviorProcessor<Boolean>
fun <T> Observable<T>.bindToBehavior(bp: BehaviorProcessor<Boolean>): Observable<T> = this
        .takeUntil(bp.toObservable().skipWhile { it })

//rx default mainThread
fun <T> Observable<T>.defThread(): Observable<T> = this
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())


//retry when error
fun <T> Observable<T>.defRetry(count: Int = 10, period: Long = 2): Observable<T> {
    var currentCount: Int = 0
    return this
            .observeOn(Schedulers.io())
            .retryWhen {
                it
                        .zipWith(Observable.range(1, count + 1), BiFunction<Throwable, Int, Throwable> { t1, t2 ->
                            currentCount = t2
                            t1
                        })
                        .flatMap {
                            if (ExceptionParseMgr.Instance.isMatchException(it) == Error.NetWork && currentCount < count + 1) {
                                Observable.timer(period, TimeUnit.SECONDS)
                            } else {
                                Observable.error(it)
                            }
                        }
            }
}

//网络不佳时重试，仅页面销毁才取消，保证重试策略的异常指向得到执行
//避免在重试请求执行期间页面不可见销毁请求，而对应的View状态得不到切换
//因为重试请求一直在发起，所以需要绑定外部的BehaviorProcessor，避免新请求执行时，重试仍然在执行
fun <T> Observable<T>.defPolicy_Retry(lifecycle: LifecycleProvider<*>): Observable<T> {

    val o = this.defThread().defRetry()
    return if (lifecycle is RxAppCompatActivity) {
        o.bindUntilEvent(lifecycle as LifecycleProvider<ActivityEvent>, ActivityEvent.DESTROY)
    } else {
        o.bindUntilEvent(lifecycle as LifecycleProvider<FragmentEvent>, FragmentEvent.DESTROY_VIEW)
    }
}

fun <T> Observable<T>.defPolicy(lifecycle: LifecycleProvider<*>): Observable<T> = this
        .defThread()
        .bindToLifecycle(lifecycle)


fun BasePresenter<*>.getBehavior(behavior: BehaviorProcessor<Boolean>?): BehaviorProcessor<Boolean> {
    //set last behavior
    behavior?.onNext(false)
    //reset behavior
    return BehaviorProcessor.create()
}

//rxPermissions【https://ww4.sinaimg.cn/large/6a195423jw1ezwpc11cs0j20hr0majwm.jpg】
fun Activity.requestAllPermission(rxPermissions: RxPermissions,
                                  success: () -> Unit,
                                  vararg permissions: String) {
    rxPermissions.request(*permissions)
            .subscribe {
                if (it) {
                    success.invoke()
                } else {
                    ToastUtils.showShortSafe("permission request fail")
                }
            }
}

//error: (denyName: String) -> Unit = { ToastUtils.showShortSafe("$it permission request fail") }
fun Activity.requestEachPermission(rxPermissions: RxPermissions,
                                   success: () -> Unit,
                                   vararg permissions: String) {
    rxPermissions.requestEach(*permissions)
            .subscribe {
                if (it.granted) {
                    success.invoke()
                } else if (it.shouldShowRequestPermissionRationale) {
                    ToastUtils.showShortSafe("${it.name} permission request fail")
                } else {
                    ToastUtils.showShortSafe("${it.name} permission request fail")
                }
            }
}


