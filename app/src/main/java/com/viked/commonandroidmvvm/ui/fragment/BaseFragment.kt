package com.viked.commonandroidmvvm.ui.fragment

import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.crashlytics.android.answers.CustomEvent
import com.viked.commonandroidmvvm.di.Injectable
import com.viked.commonandroidmvvm.log.Analytic
import com.viked.commonandroidmvvm.log.log
import com.viked.commonandroidmvvm.text.TextWrapper
import com.viked.commonandroidmvvm.ui.activity.BaseActivity
import com.viked.commonandroidmvvm.ui.adapters.AdapterDelegate
import com.viked.commonandroidmvvm.ui.binding.addOnPropertyChangeListener
import com.viked.commonandroidmvvm.ui.common.AutoClearedValue
import com.viked.commonandroidmvvm.ui.common.Cancelable
import com.viked.commonandroidmvvm.ui.common.delegate.error.DialogErrorDelegate
import com.viked.commonandroidmvvm.ui.common.delegate.error.ErrorDelegate
import com.viked.commonandroidmvvm.ui.common.delegate.progress.DialogProgressDelegate
import com.viked.commonandroidmvvm.ui.common.delegate.progress.ProgressDelegate
import javax.inject.Inject

/**
 * Created by yevgeniishein on 10/9/17.
 */
abstract class BaseFragment<T : BaseViewModel, B : ViewDataBinding> : Fragment(), Injectable, Cancelable {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var analytic: Analytic

    lateinit var progressDelegate: AutoClearedValue<ProgressDelegate>

    lateinit var errorDelegate: AutoClearedValue<ErrorDelegate>

    lateinit var viewModel: AutoClearedValue<T>

    lateinit var binding: AutoClearedValue<B>

    lateinit var adapters: AutoClearedValue<MutableList<AdapterDelegate>>

    abstract val layoutId: Int

    abstract val viewModelClass: Class<T>

    abstract fun setViewModelToBinding(binding: B, viewModel: T)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val viewModel = ViewModelProviders.of(this, viewModelFactory).get(viewModelClass)
        val binding = binding.value
        this.viewModel = AutoClearedValue(this, viewModel)
        val activity = activity()
        if (binding != null && activity != null) {
            arguments?.run { initArguments(viewModel, this) }
            adapters = AutoClearedValue(this, mutableListOf())
            viewModel.onInit()
            viewModel.loadData()
            setViewModelToBinding(binding, viewModel)
            initToolbar(activity, binding, viewModel)
            initView(binding, viewModel)
            progressDelegate = AutoClearedValue(this, initProgressDelegate(binding, viewModel, activity))
            errorDelegate = AutoClearedValue(this, initErrorDelegate(binding, viewModel, activity))
            adapters.value?.forEach { it.subscribe() }
            logStartEvent()
        } else {
            RuntimeException("BaseFragment has empty params\nbinding: ${this.binding.value}\nviewModel: ${this.viewModel.value}").log()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val dataBinding = DataBindingUtil
                .inflate<B>(inflater, layoutId, container, false)
        binding = AutoClearedValue(this, dataBinding)
        onCreateAdditionalViews(inflater, container)
        return dataBinding.root
    }

    override fun onDestroyView() {
        adapters.value?.forEach { it.unsubscribe() }
        viewModel.value?.onCleared()
        super.onDestroyView()
    }

    override fun handleOnBackPressed() = viewModel.value?.progress?.get() ?: false

    open fun onCreateAdditionalViews(inflater: LayoutInflater?, container: ViewGroup?) {
        //Init toolbar view if need
    }

    open fun initToolbar(activity: BaseActivity, binding: B, viewModel: T) {
        viewModel.title.addOnPropertyChangeListener { setTitle(it.get()) }

        //Set title if need
    }

    open fun initView(binding: B, viewModel: T) {
        //Set clicks, other view features
    }

    open fun initProgressDelegate(binding: B, viewModel: T, activity: BaseActivity) =
            activity()?.progressDelegate ?: DialogProgressDelegate(activity)

    open fun initErrorDelegate(binding: B, viewModel: T, activity: BaseActivity) =
            activity()?.errorDelegate ?: DialogErrorDelegate(activity)

    open fun initArguments(viewModel: T, arguments: Bundle) {
        //Set initial data to view model
    }

    fun activity() = activity as BaseActivity?

    override fun onPause() {
        val viewModel = viewModel.value
        if (viewModel != null) {
            progressDelegate.value?.unsubscribe(viewModel.progress)
            errorDelegate.value?.unsubscribe(viewModel.error)
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        val viewModel = viewModel.value
        if (viewModel != null) {
            progressDelegate.value?.subscribe(viewModel.progress)
            errorDelegate.value?.subscribe(viewModel.error)
        }
    }

    protected fun setTitle(title: TextWrapper?) {
        val activity = activity()
        if (activity != null && title != null) {
            val newTitle = title[activity]
            if (newTitle.isNotEmpty()) {
                activity.title = newTitle
            }
        }
    }

    protected fun addAdapterDelegate(adapterDelegate: AdapterDelegate) {
        adapters.value?.add(adapterDelegate)
    }

    private fun logStartEvent() {
        analytic.log(CustomEvent("Screen viewed").putCustomAttribute("name", this::class.java.simpleName))
    }
}