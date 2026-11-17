package com.example

import android.app.Application
import com.airbnb.lottie.LottieCompositionFactory
import com.example.billing.ProBillingManager
import com.example.data.NotchSettingsRepository
import com.example.service.NotchRuntimeCoordinator

class MatusNotchApplication : Application() {

  lateinit var settingsRepository: NotchSettingsRepository
    private set

  lateinit var billingManager: ProBillingManager
    private set

  lateinit var coordinator: NotchRuntimeCoordinator
    private set

  override fun onCreate() {
    super.onCreate()
    // Pre-warm all 4 Maskot Lottie compositions into LottieCompositionCache for zero-blink switching
    LottieCompositionFactory.fromRawRes(this, R.raw.mascot_idle)
    LottieCompositionFactory.fromRawRes(this, R.raw.mascot_music)
    LottieCompositionFactory.fromRawRes(this, R.raw.mascot_sleeping)
    LottieCompositionFactory.fromRawRes(this, R.raw.mascot_wake_wave)

    settingsRepository = NotchSettingsRepository(this)
    billingManager = ProBillingManager(this, settingsRepository)
    coordinator = NotchRuntimeCoordinator(this, settingsRepository, billingManager)
  }
}
