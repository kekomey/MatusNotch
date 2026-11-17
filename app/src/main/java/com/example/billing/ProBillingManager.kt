package com.example.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.BuildConfig
import com.example.data.NotchSettingsRepository
import com.example.model.EntitlementResolver
import com.example.model.ProEntitlementState
import com.example.model.ProPlanTier
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProBillingManager(
  private val appContext: Context,
  private val settingsRepository: NotchSettingsRepository,
) : PurchasesUpdatedListener {

  private data class ResolvedSubscriptionOffer(
    val tier: ProPlanTier,
    val productDetails: ProductDetails,
    val offerToken: String,
    val formattedPrice: String?,
    val hasTrialOffer: Boolean,
  )

  private data class PendingLaunchRequest(
    val activityRef: WeakReference<Activity>,
    val tier: ProPlanTier,
  )

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val _entitlementState = MutableStateFlow(ProEntitlementState())
  val entitlementState: StateFlow<ProEntitlementState> = _entitlementState.asStateFlow()

  private var resolvedOffers: Map<ProPlanTier, ResolvedSubscriptionOffer> = emptyMap()
  private var isConnecting = false
  private var hasAttemptedConnection = false
  private var lastSetupResponseCode: Int? = null
  private var lastSetupDebugMessage: String = ""
  private var pendingLaunch: PendingLaunchRequest? = null
  private var pendingRestoreFeedback = false

  private var billingClient: BillingClient = createBillingClient()

  init {
    scope.launch {
      settingsRepository.proStateFlow.collect { persisted ->
        _entitlementState.update { current ->
          current.copy(isDebugUnlocked = persisted.debugProOverride)
        }
      }
    }
    connectBillingClient(isManualRetry = false)
  }

  private fun createBillingClient(): BillingClient =
    BillingClient.newBuilder(appContext)
      .setListener(this)
      .enablePendingPurchases(
        PendingPurchasesParams.newBuilder()
          .enableOneTimeProducts()
          .enablePrepaidPlans()
          .build()
      )
      .build()

  fun isProUnlockedNow(): Boolean = _entitlementState.value.isProUnlocked()

  fun clearBillingMessage() {
    _entitlementState.update { it.copy(billingMessage = null) }
  }

  fun selectPlan(tier: ProPlanTier) {
    _entitlementState.update { it.copy(selectedPlanTier = tier) }
  }

  fun refresh() {
    if (billingClient.isReady) {
      querySubscriptionDetails()
      queryExistingSubscriptions(showFeedback = false)
      return
    }
    // Avoid repeatedly re-binding when Google Play reported BILLING_UNAVAILABLE or is connecting.
    if (
      !isConnecting &&
        billingClient.connectionState != BillingClient.ConnectionState.CONNECTING &&
        lastSetupResponseCode != BillingClient.BillingResponseCode.BILLING_UNAVAILABLE
    ) {
      connectBillingClient(isManualRetry = false)
    }
  }

  fun setDebugProOverride(enabled: Boolean, onUpdated: (() -> Unit)? = null) {
    if (!BuildConfig.DEBUG) return
    scope.launch {
      settingsRepository.setDebugProOverride(enabled)
      _entitlementState.update {
        it.copy(
          isDebugUnlocked = enabled,
          billingMessage =
            if (enabled) "Debug Pro preview enabled" else "Debug Pro preview disabled",
        )
      }
      if (enabled) onUpdated?.invoke()
    }
  }

  fun launchSubscriptionFlow(
    activity: Activity,
    tier: ProPlanTier = _entitlementState.value.selectedPlanTier,
  ) {
    _entitlementState.update { it.copy(selectedPlanTier = tier) }

    if (!billingClient.isReady) {
      pendingLaunch = PendingLaunchRequest(WeakReference(activity), tier)
      _entitlementState.update { it.copy(isPurchasing = true, billingMessage = null) }
      if (!isConnecting && billingClient.connectionState != BillingClient.ConnectionState.CONNECTING) {
        connectBillingClient(isManualRetry = true)
      }
      return
    }

    val offer = resolvedOffers[tier]
    if (offer == null) {
      pendingLaunch = PendingLaunchRequest(WeakReference(activity), tier)
      _entitlementState.update { it.copy(isPurchasing = true, billingMessage = null) }
      querySubscriptionDetails()
      return
    }

    launchResolvedOffer(activity, offer)
  }

  private fun launchResolvedOffer(
    activity: Activity,
    offer: ResolvedSubscriptionOffer,
  ) {
    if (activity.isFinishing || activity.isDestroyed) {
      _entitlementState.update { it.copy(isPurchasing = false) }
      return
    }

    val productParams =
      BillingFlowParams.ProductDetailsParams.newBuilder()
        .setProductDetails(offer.productDetails)
        .setOfferToken(offer.offerToken)
        .build()

    val flowParams =
      BillingFlowParams.newBuilder()
        .setProductDetailsParamsList(listOf(productParams))
        .build()

    _entitlementState.update { it.copy(isPurchasing = true, billingMessage = null) }
    val result = billingClient.launchBillingFlow(activity, flowParams)
    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
      _entitlementState.update {
        it.copy(
          isPurchasing = false,
          billingMessage =
            result.debugMessage.ifBlank {
              "Could not open Google Play subscription sheet (${result.responseCode})"
            },
        )
      }
    }
  }

  fun restorePurchases() {
    if (!billingClient.isReady) {
      pendingRestoreFeedback = true
      _entitlementState.update {
        it.copy(billingMessage = "Connecting to Google Play to check active subscriptions...")
      }
      if (!isConnecting && billingClient.connectionState != BillingClient.ConnectionState.CONNECTING) {
        connectBillingClient(isManualRetry = true)
      }
      return
    }
    queryExistingSubscriptions(showFeedback = true)
  }

  override fun onPurchasesUpdated(
    billingResult: BillingResult,
    purchases: MutableList<Purchase>?,
  ) {
    when (billingResult.responseCode) {
      BillingClient.BillingResponseCode.OK -> {
        processPurchases(purchases.orEmpty(), showFeedback = true)
      }

      BillingClient.BillingResponseCode.USER_CANCELED -> {
        _entitlementState.update {
          it.copy(isPurchasing = false, billingMessage = "Subscription checkout canceled")
        }
      }

      BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
        queryExistingSubscriptions(showFeedback = true)
      }

      else -> {
        _entitlementState.update {
          it.copy(
            isPurchasing = false,
            billingMessage =
              billingResult.debugMessage.ifBlank {
                "Play Billing error (${billingResult.responseCode})"
              },
          )
        }
      }
    }
  }

  private fun connectBillingClient(isManualRetry: Boolean) {
    if (billingClient.isReady || isConnecting) return
    if (billingClient.connectionState == BillingClient.ConnectionState.CONNECTING) return

    // Recreating BillingClient after a failed/closed connection prevents BillingClientImpl's
    // internal unbindService("Service not registered") warning when startConnection() is retried.
    if (
      hasAttemptedConnection ||
        billingClient.connectionState == BillingClient.ConnectionState.CLOSED
    ) {
      runCatching { billingClient.endConnection() }
      billingClient = createBillingClient()
    }

    hasAttemptedConnection = true
    isConnecting = true
    val client = billingClient

    runCatching {
      client.startConnection(
        object : BillingClientStateListener {
          override fun onBillingSetupFinished(billingResult: BillingResult) {
            if (client !== billingClient) return
            isConnecting = false
            lastSetupResponseCode = billingResult.responseCode
            lastSetupDebugMessage = billingResult.debugMessage

            val ready = billingResult.responseCode == BillingClient.BillingResponseCode.OK
            _entitlementState.update { it.copy(isBillingReady = ready) }

            if (ready) {
              val wantsRestoreFeedback = pendingRestoreFeedback
              pendingRestoreFeedback = false
              querySubscriptionDetails()
              queryExistingSubscriptions(showFeedback = wantsRestoreFeedback)
            } else {
              val pending = pendingLaunch
              pendingLaunch = null
              val wantsRestoreFeedback = pendingRestoreFeedback
              pendingRestoreFeedback = false
              val message =
                formatSetupErrorMessage(
                  responseCode = billingResult.responseCode,
                  debugMessage = billingResult.debugMessage,
                )
              _entitlementState.update { current ->
                current.copy(
                  isPurchasing = false,
                  billingMessage =
                    if (pending != null || wantsRestoreFeedback || isManualRetry) message
                    else current.billingMessage,
                )
              }
            }
          }

          override fun onBillingServiceDisconnected() {
            if (client !== billingClient) return
            isConnecting = false
            _entitlementState.update { it.copy(isBillingReady = false) }
          }
        }
      )
    }.onFailure { error ->
      isConnecting = false
      val pending = pendingLaunch
      pendingLaunch = null
      pendingRestoreFeedback = false
      _entitlementState.update { current ->
        current.copy(
          isBillingReady = false,
          isPurchasing = false,
          billingMessage =
            if (pending != null || isManualRetry) {
              error.localizedMessage ?: "Google Play Billing unavailable on this device"
            } else {
              current.billingMessage
            },
        )
      }
    }
  }

  private fun formatSetupErrorMessage(responseCode: Int, debugMessage: String): String {
    val detail = debugMessage.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    return when (responseCode) {
      BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
        "Google Play Billing unavailable on this device/emulator$detail. Sign into Google Play or use Debug Pro Preview in Settings."
      BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
      BillingClient.BillingResponseCode.NETWORK_ERROR,
      BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
        "Cannot reach Google Play Store$detail. Check your internet connection and try again."
      BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED ->
        "Subscriptions are not supported by this Google Play Store version$detail."
      else ->
        "Google Play setup failed ($responseCode)$detail."
    }
  }

  private fun querySubscriptionDetails() {
    val products =
      EntitlementResolver.ALL_SUBSCRIPTION_PRODUCT_IDS.map { productId ->
        QueryProductDetailsParams.Product.newBuilder()
          .setProductId(productId)
          .setProductType(BillingClient.ProductType.SUBS)
          .build()
      }

    val params =
      QueryProductDetailsParams.newBuilder()
        .setProductList(products)
        .build()

    billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
      if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
        val pending = pendingLaunch
        pendingLaunch = null
        if (pending != null) {
          _entitlementState.update {
            it.copy(
              isPurchasing = false,
              billingMessage =
                billingResult.debugMessage.ifBlank {
                  "Could not load Google Play subscription plans (${billingResult.responseCode})"
                },
            )
          }
        }
        return@queryProductDetailsAsync
      }

      val discovered = mutableMapOf<ProPlanTier, ResolvedSubscriptionOffer>()
      for (details in queryResult.productDetailsList) {
        val offers = details.subscriptionOfferDetails.orEmpty()
        for (offer in offers) {
          val phases = offer.pricingPhases.pricingPhaseList
          val hasTrial = phases.any { it.priceAmountMicros == 0L }
          val paidPhase = phases.lastOrNull { it.priceAmountMicros > 0L } ?: phases.lastOrNull()
          val period = paidPhase?.billingPeriod.orEmpty()
          val basePlan = offer.basePlanId.lowercase()

          val tier =
            when {
              details.productId == ProPlanTier.MONTHLY.productId ||
                basePlan.contains("month") ||
                period == "P1M" -> ProPlanTier.MONTHLY
              details.productId == ProPlanTier.YEARLY.productId ||
                basePlan.contains("year") ||
                basePlan.contains("annual") ||
                period == "P1Y" -> ProPlanTier.YEARLY
              else -> null
            } ?: continue

          val candidate =
            ResolvedSubscriptionOffer(
              tier = tier,
              productDetails = details,
              offerToken = offer.offerToken,
              formattedPrice = paidPhase?.formattedPrice,
              hasTrialOffer = hasTrial,
            )

          // Prefer an introductory free-trial offer token over base-only when available
          val existing = discovered[tier]
          if (existing == null || (!existing.hasTrialOffer && candidate.hasTrialOffer)) {
            discovered[tier] = candidate
          }
        }
      }

      resolvedOffers = discovered
      val monthlyOffer = discovered[ProPlanTier.MONTHLY]
      val yearlyOffer = discovered[ProPlanTier.YEARLY]
      _entitlementState.update { current ->
        current.copy(
          monthlyPrice = monthlyOffer?.formattedPrice ?: current.monthlyPrice,
          yearlyPrice = yearlyOffer?.formattedPrice ?: current.yearlyPrice,
          hasMonthlyTrialOffer = monthlyOffer?.hasTrialOffer ?: current.hasMonthlyTrialOffer,
          hasYearlyTrialOffer = yearlyOffer?.hasTrialOffer ?: current.hasYearlyTrialOffer,
        )
      }

      val pending = pendingLaunch
      pendingLaunch = null
      if (pending != null) {
        val activity = pending.activityRef.get()
        val offer = discovered[pending.tier]
        if (activity != null && offer != null) {
          launchResolvedOffer(activity, offer)
        } else {
          _entitlementState.update {
            it.copy(
              isPurchasing = false,
              billingMessage =
                "Subscription plan '${pending.tier.productId}' (${pending.tier.fallbackPrice}${pending.tier.billingSuffix}) not found on Google Play. Verify Play Console subscription & 4-day trial setup.",
            )
          }
        }
      }
    }
  }

  private fun queryExistingSubscriptions(showFeedback: Boolean) {
    val params =
      QueryPurchasesParams.newBuilder()
        .setProductType(BillingClient.ProductType.SUBS)
        .build()

    billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
      if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
        processPurchases(purchases, showFeedback = showFeedback)
      } else if (showFeedback) {
        _entitlementState.update {
          it.copy(
            isPurchasing = false,
            billingMessage =
              billingResult.debugMessage.ifBlank { "Unable to verify Google Play subscriptions" },
          )
        }
      }
    }
  }

  private fun processPurchases(
    purchases: List<Purchase>,
    showFeedback: Boolean,
  ) {
    val matchingIds = EntitlementResolver.ALL_SUBSCRIPTION_PRODUCT_IDS.toSet()

    val activeSub =
      purchases.firstOrNull { purchase ->
        purchase.products.any { it in matchingIds } &&
          purchase.purchaseState == Purchase.PurchaseState.PURCHASED
      }

    val pendingSub =
      purchases.firstOrNull { purchase ->
        purchase.products.any { it in matchingIds } &&
          purchase.purchaseState == Purchase.PurchaseState.PENDING
      }

    if (activeSub != null) {
      val resolvedTier =
        when {
          activeSub.products.contains(ProPlanTier.YEARLY.productId) -> ProPlanTier.YEARLY
          activeSub.products.contains(ProPlanTier.MONTHLY.productId) -> ProPlanTier.MONTHLY
          else -> _entitlementState.value.selectedPlanTier
        }

      if (!activeSub.isAcknowledged) {
        val ackParams =
          AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(activeSub.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(ackParams) { ackResult ->
          if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _entitlementState.update {
              it.copy(
                isPlaySubscribed = true,
                activePlanTier = resolvedTier,
                isPurchasing = false,
                billingMessage = if (showFeedback) "Matus Pro subscription active!" else it.billingMessage,
              )
            }
          }
        }
      }

      _entitlementState.update {
        it.copy(
          isPlaySubscribed = true,
          activePlanTier = resolvedTier,
          isPurchasing = false,
          billingMessage = if (showFeedback) "Matus Pro subscription active!" else it.billingMessage,
        )
      }
    } else if (pendingSub != null) {
      _entitlementState.update {
        it.copy(
          isPurchasing = false,
          billingMessage = "Subscription pending — Matus Pro activates once Play confirms",
        )
      }
    } else {
      _entitlementState.update {
        it.copy(
          isPlaySubscribed = false,
          activePlanTier = null,
          isPurchasing = false,
          billingMessage =
            if (showFeedback) "No active Matus Pro subscription found on this Google account"
            else it.billingMessage,
        )
      }
    }
  }
}
