package com.example.model

import com.example.BuildConfig

enum class ProPlanTier(
  val productId: String,
  val basePlanId: String,
  val title: String,
  val shortPrice: String,
  val fallbackPrice: String,
  val billingSuffix: String,
  val periodLabel: String,
  val savingsBadge: String? = null,
) {
  MONTHLY(
    productId = "matus_pro_monthly",
    basePlanId = "monthly",
    title = "Monthly",
    shortPrice = "$1/mo",
    fallbackPrice = "$1.00",
    billingSuffix = "/mo",
    periodLabel = "month",
    savingsBadge = null,
  ),
  YEARLY(
    productId = "matus_pro_yearly",
    basePlanId = "yearly",
    title = "Annual",
    shortPrice = "$5/yr",
    fallbackPrice = "$5.00",
    billingSuffix = "/yr",
    periodLabel = "year",
    savingsBadge = "Save 58%",
  ),
}

data class PersistedProState(
  val debugProOverride: Boolean = false,
)

data class ProEntitlementState(
  val isPlaySubscribed: Boolean = false,
  val activePlanTier: ProPlanTier? = null,
  val selectedPlanTier: ProPlanTier = ProPlanTier.YEARLY,
  val isDebugUnlocked: Boolean = false,
  val monthlyPrice: String? = null,
  val yearlyPrice: String? = null,
  val hasMonthlyTrialOffer: Boolean = true,
  val hasYearlyTrialOffer: Boolean = true,
  val isBillingReady: Boolean = false,
  val isPurchasing: Boolean = false,
  val billingMessage: String? = null,
) {
  fun isProUnlocked(isDebugBuild: Boolean = BuildConfig.DEBUG): Boolean =
    isPlaySubscribed || (isDebugBuild && isDebugUnlocked)

  fun displayPrice(tier: ProPlanTier = selectedPlanTier): String =
    when (tier) {
      ProPlanTier.MONTHLY -> monthlyPrice?.takeIf { it.isNotBlank() } ?: tier.fallbackPrice
      ProPlanTier.YEARLY -> yearlyPrice?.takeIf { it.isNotBlank() } ?: tier.fallbackPrice
    }

  fun displayPriceWithPeriod(tier: ProPlanTier = selectedPlanTier): String =
    "${displayPrice(tier)}${tier.billingSuffix}"

  fun hasTrialOffer(tier: ProPlanTier = selectedPlanTier): Boolean =
    when (tier) {
      ProPlanTier.MONTHLY -> hasMonthlyTrialOffer
      ProPlanTier.YEARLY -> hasYearlyTrialOffer
    }
}

data class ProPreviewSelection(
  val accentPreset: AccentPreset? = null,
  val customAccentHex: Long? = null,
  val borderStyle: NotchBorderStyle? = null,
  val glowStyle: NotchGlowStyle? = null,
  val previewWidgetType: NotchWidgetType? = null,
  val notificationFilterMode: NotificationFilterMode? = null,
) {
  val hasAnyPreview: Boolean
    get() =
      accentPreset != null ||
        customAccentHex != null ||
        borderStyle != null ||
        glowStyle != null ||
        previewWidgetType != null ||
        notificationFilterMode != null

  fun summaryText(): String {
    val tags = buildList {
      if (accentPreset == AccentPreset.CUSTOM || customAccentHex != null) add("Color Wheel")
      if (borderStyle != null) add(borderStyle.label)
      if (glowStyle != null) add(glowStyle.label)
      if (previewWidgetType != null) add(previewWidgetType.title)
      if (notificationFilterMode != null) add(notificationFilterMode.label)
    }
    return tags.joinToString(" • ").ifEmpty { "Matus Pro customization" }
  }
}

object EntitlementResolver {
  const val SUB_GROUP_ID = "matus_pro"
  const val MONTHLY_PRODUCT_ID = "matus_pro_monthly"
  const val YEARLY_PRODUCT_ID = "matus_pro_yearly"
  const val TRIAL_DAYS = 4

  val ALL_SUBSCRIPTION_PRODUCT_IDS =
    listOf(MONTHLY_PRODUCT_ID, YEARLY_PRODUCT_ID, SUB_GROUP_ID)

  fun isProAccent(preset: AccentPreset): Boolean = preset == AccentPreset.CUSTOM

  fun isProBorder(style: NotchBorderStyle): Boolean = style != NotchBorderStyle.HAIRLINE

  fun isProGlow(style: NotchGlowStyle): Boolean = style != NotchGlowStyle.LED_RACE

  fun isProWidget(type: NotchWidgetType): Boolean =
    type == NotchWidgetType.CALCULATOR || type == NotchWidgetType.FOCUS_TIMER

  fun isProFilterMode(mode: NotificationFilterMode): Boolean =
    mode != NotificationFilterMode.ALL_APPS

  fun effectiveConfig(
    persisted: NotchConfig,
    isPro: Boolean,
  ): NotchConfig {
    if (isPro) return persisted

    val freeAppearance =
      persisted.appearance.copy(
        accentPreset =
          if (isProAccent(persisted.appearance.accentPreset)) AccentPreset.CYAN
          else persisted.appearance.accentPreset,
        borderStyle =
          if (isProBorder(persisted.appearance.borderStyle)) NotchBorderStyle.HAIRLINE
          else persisted.appearance.borderStyle,
        glowStyle =
          if (isProGlow(persisted.appearance.glowStyle)) NotchGlowStyle.LED_RACE
          else persisted.appearance.glowStyle,
      )

    val freeSlots =
      persisted.widgetSlots.map { slot ->
        if (isProWidget(slot.type)) slot.copy(enabled = false) else slot
      }

    return persisted.copy(
      appearance = freeAppearance,
      widgetSlots = freeSlots,
      notificationFilterMode =
        if (isProFilterMode(persisted.notificationFilterMode)) NotificationFilterMode.ALL_APPS
        else persisted.notificationFilterMode,
    )
  }

  fun applyStudioPreview(
    effectiveBase: NotchConfig,
    preview: ProPreviewSelection,
    isPro: Boolean,
  ): NotchConfig {
    if (isPro || !preview.hasAnyPreview) return effectiveBase

    val previewAppearance =
      effectiveBase.appearance.copy(
        accentPreset = preview.accentPreset ?: effectiveBase.appearance.accentPreset,
        customAccentHex = preview.customAccentHex ?: effectiveBase.appearance.customAccentHex,
        borderStyle = preview.borderStyle ?: effectiveBase.appearance.borderStyle,
        glowStyle = preview.glowStyle ?: effectiveBase.appearance.glowStyle,
      )

    val previewWidget = preview.previewWidgetType
    val previewSlots =
      if (previewWidget != null) {
        effectiveBase.widgetSlots.map { slot ->
          if (slot.type == previewWidget) slot.copy(enabled = true) else slot
        }
      } else {
        effectiveBase.widgetSlots
      }

    return effectiveBase.copy(
      appearance = previewAppearance,
      widgetSlots = previewSlots,
      notificationFilterMode = preview.notificationFilterMode ?: effectiveBase.notificationFilterMode,
    )
  }

  fun mergePreviewIntoConfig(
    persisted: NotchConfig,
    preview: ProPreviewSelection,
  ): NotchConfig {
    if (!preview.hasAnyPreview) return persisted

    val nextAppearance =
      persisted.appearance.copy(
        accentPreset = preview.accentPreset ?: persisted.appearance.accentPreset,
        customAccentHex = preview.customAccentHex ?: persisted.appearance.customAccentHex,
        borderStyle = preview.borderStyle ?: persisted.appearance.borderStyle,
        glowStyle = preview.glowStyle ?: persisted.appearance.glowStyle,
      )

    val previewWidget = preview.previewWidgetType
    val nextSlots =
      if (previewWidget != null) {
        persisted.widgetSlots.map { slot ->
          if (slot.type == previewWidget) slot.copy(enabled = true) else slot
        }
      } else {
        persisted.widgetSlots
      }

    return persisted.copy(
      appearance = nextAppearance,
      widgetSlots = nextSlots,
      notificationFilterMode = preview.notificationFilterMode ?: persisted.notificationFilterMode,
    )
  }
}
