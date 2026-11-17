package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.MascotState
import com.example.model.NotchConfig
import com.example.model.ProEntitlementState
import com.example.model.ProPlanTier
import com.example.model.ProPreviewSelection
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ProLockBadge(
  label: String = "PRO",
  locked: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val primary = MaterialTheme.colorScheme.primary
  Row(
    modifier =
      modifier
        .clip(RoundedCornerShape(50))
        .background(primary.copy(alpha = 0.16f))
        .border(0.75.dp, primary.copy(alpha = 0.50f), RoundedCornerShape(50))
        .padding(horizontal = 6.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Icon(
      imageVector = if (locked) Icons.Rounded.Lock else Icons.Rounded.WorkspacePremium,
      contentDescription = if (locked) "Pro feature locked" else "Pro exclusive feature",
      tint = primary,
      modifier = Modifier.size(10.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
      color = primary,
    )
  }
}

@Composable
fun ProPreviewCheckoutBar(
  visible: Boolean,
  preview: ProPreviewSelection,
  entitlement: ProEntitlementState,
  onResetPreview: () -> Unit,
  onStartPlayTrial: () -> Unit,
  onOpenPaywall: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val primary = MaterialTheme.colorScheme.primary
  val secondary = MaterialTheme.colorScheme.secondary
  val selectedTier = entitlement.selectedPlanTier

  AnimatedVisibility(
    visible = visible,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
    modifier = modifier,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .background(
            Brush.horizontalGradient(
              listOf(primary.copy(alpha = 0.18f), secondary.copy(alpha = 0.14f))
            )
          )
          .border(1.dp, primary.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
          .padding(horizontal = 14.dp, vertical = 12.dp)
          .testTag("pro_preview_checkout_bar"),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier =
            Modifier.size(34.dp)
              .clip(CircleShape)
              .background(primary.copy(alpha = 0.22f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.WorkspacePremium,
            contentDescription = null,
            tint = primary,
            modifier = Modifier.size(18.dp),
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Previewing Matus Pro",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary,
          )
          Text(
            text = "${preview.summaryText()} • Applied when subscribed",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        TextButton(
          onClick = onResetPreview,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("pro_preview_reset_btn"),
        ) {
          Text("Reset", color = TextSecondary)
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = onOpenPaywall,
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("pro_preview_unlock_btn"),
        ) {
          Text(
            text = "Plans (\$1/mo • \$5/yr)",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Button(
          onClick = onStartPlayTrial,
          enabled = !entitlement.isPurchasing,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = primary,
              contentColor = Color.Black,
            ),
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("pro_preview_trial_btn"),
        ) {
          Text(
            text =
              if (entitlement.isPurchasing) "Opening Play..."
              else "4-Day Free Trial (${entitlement.displayPriceWithPeriod(selectedTier)})",
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      if (!entitlement.billingMessage.isNullOrBlank()) {
        Text(
          text = entitlement.billingMessage,
          style = MaterialTheme.typography.bodySmall,
          color = StatusWarning,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatusProPaywallSheet(
  config: NotchConfig,
  entitlement: ProEntitlementState,
  onSelectPlan: (ProPlanTier) -> Unit,
  onLaunchSubscription: (ProPlanTier) -> Unit,
  onRestorePurchase: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val primary = MaterialTheme.colorScheme.primary
  val secondary = MaterialTheme.colorScheme.secondary
  val isPro = entitlement.isProUnlocked()
  val selectedTier = entitlement.selectedPlanTier
  val hasTrial = entitlement.hasTrialOffer(selectedTier)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = ObsidianSurface,
    contentColor = TextPrimary,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 8.dp)
          .testTag("matus_pro_paywall_sheet"),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Hero Mascot + Crown Header
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
              Brush.linearGradient(
                listOf(primary.copy(alpha = 0.20f), secondary.copy(alpha = 0.14f), ObsidianElevated)
              )
            )
            .border(1.dp, primary.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Box(
          modifier =
            Modifier.size(58.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.5f))
              .border(1.5.dp, primary.copy(alpha = 0.7f), CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          MascotView(
            state = MascotState.WAKING_UP,
            appearance = config.appearance,
            size = 48.dp,
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.WorkspacePremium,
              contentDescription = null,
              tint = primary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "Matus Pro",
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
              color = TextPrimary,
            )
          }
          Text(
            text =
              when {
                entitlement.isPlaySubscribed ->
                  "Active ${entitlement.activePlanTier?.title ?: "Pro"} Google Play subscription"
                entitlement.isDebugUnlocked -> "Unlocked via Debug Preview toggle"
                else -> "4-day free trial via Google Play • \$1/mo or \$5/yr"
              },
            style = MaterialTheme.typography.bodyMedium,
            color = if (isPro) StatusSuccess else TextSecondary,
          )
        }
      }

      if (!entitlement.billingMessage.isNullOrBlank()) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(StatusWarning.copy(alpha = 0.14f))
              .border(1.dp, StatusWarning.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
              .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = entitlement.billingMessage,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
          )
        }
      }

      // Feature List
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ObsidianElevated)
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        ProBenefitRow(
          icon = Icons.Rounded.ColorLens,
          title = "HSV Color Wheel",
          subtitle = "Create any custom dual-tone accent glow for your notch",
          tint = primary,
        )
        ProBenefitRow(
          icon = Icons.Rounded.AutoAwesome,
          title = "Premium Borders & Glow Animations",
          subtitle = "Neon Rim, Cyber Frame, Dual Comet, Breathing Halo & Prism Wave",
          tint = primary,
        )
        ProBenefitRow(
          icon = Icons.Rounded.Calculate,
          title = "Calculator & Focus Timer Widgets",
          subtitle = "Instant split keypad and Pomodoro timer inside the expanded notch",
          tint = primary,
        )
        ProBenefitRow(
          icon = Icons.Rounded.FilterList,
          title = "Per-App Notification Filter",
          subtitle = "Allowlist or mute specific apps from expanding your notch",
          tint = primary,
        )
        ProBenefitRow(
          icon = Icons.Rounded.Mic,
          title = "Voice Assistant & Gemini AI",
          subtitle = "Long-press mascot for local commands & live Gemini AI answers",
          tint = primary,
        )
      }

      // Monthly ($1/mo) vs Annual ($5/yr) Plan Picker
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        SubscriptionPlanCard(
          tier = ProPlanTier.YEARLY,
          priceText = entitlement.displayPrice(ProPlanTier.YEARLY),
          hasTrial = entitlement.hasTrialOffer(ProPlanTier.YEARLY),
          selected = selectedTier == ProPlanTier.YEARLY,
          primary = primary,
          onClick = { onSelectPlan(ProPlanTier.YEARLY) },
          modifier = Modifier.weight(1f).testTag("plan_card_yearly"),
        )
        SubscriptionPlanCard(
          tier = ProPlanTier.MONTHLY,
          priceText = entitlement.displayPrice(ProPlanTier.MONTHLY),
          hasTrial = entitlement.hasTrialOffer(ProPlanTier.MONTHLY),
          selected = selectedTier == ProPlanTier.MONTHLY,
          primary = primary,
          onClick = { onSelectPlan(ProPlanTier.MONTHLY) },
          modifier = Modifier.weight(1f).testTag("plan_card_monthly"),
        )
      }

      // Primary Google Play Billing CTA (processes 4-day free trial through Play Store)
      Button(
        onClick = { onLaunchSubscription(selectedTier) },
        enabled = !entitlement.isPlaySubscribed && !entitlement.isPurchasing,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = if (entitlement.isPlaySubscribed) StatusSuccess else primary,
            contentColor = Color.Black,
          ),
        modifier =
          Modifier.fillMaxWidth()
            .height(54.dp)
            .minimumInteractiveComponentSize()
            .testTag("paywall_subscribe_btn"),
      ) {
        if (entitlement.isPurchasing) {
          CircularProgressIndicator(
            color = Color.Black,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.size(8.dp))
          Text("Opening Google Play...")
        } else if (entitlement.isPlaySubscribed) {
          Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = null)
          Spacer(modifier = Modifier.size(8.dp))
          Text("Matus Pro Active", fontWeight = FontWeight.Bold)
        } else {
          Icon(imageVector = Icons.Rounded.WorkspacePremium, contentDescription = null)
          Spacer(modifier = Modifier.size(8.dp))
          Text(
            text =
              if (hasTrial) {
                "Start 4-Day Free Trial • Then ${entitlement.displayPriceWithPeriod(selectedTier)}"
              } else {
                "Subscribe • ${entitlement.displayPriceWithPeriod(selectedTier)}"
              },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            if (hasTrial) {
              "4 days free via Google Play, then ${entitlement.displayPriceWithPeriod(selectedTier)}. Cancel anytime."
            } else {
              "Billed via Google Play at ${entitlement.displayPriceWithPeriod(selectedTier)}. Cancel anytime."
            },
          style = MaterialTheme.typography.labelSmall,
          color = TextMuted,
          modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Row(
          modifier =
            Modifier.clip(RoundedCornerShape(50))
              .clickable(onClick = onRestorePurchase)
              .minimumInteractiveComponentSize()
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("paywall_restore_btn"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Restore,
            contentDescription = "Restore purchase",
            tint = primary,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = "Restore",
            style = MaterialTheme.typography.labelMedium,
            color = primary,
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))
    }
  }
}

@Composable
private fun SubscriptionPlanCard(
  tier: ProPlanTier,
  priceText: String,
  hasTrial: Boolean,
  selected: Boolean,
  primary: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(18.dp))
        .background(if (selected) primary.copy(alpha = 0.16f) else ObsidianElevated)
        .border(
          width = if (selected) 1.5.dp else 1.dp,
          color = if (selected) primary else Color.White.copy(alpha = 0.10f),
          shape = RoundedCornerShape(18.dp),
        )
        .clickable(onClick = onClick)
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = tier.title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = if (selected) primary else TextPrimary,
      )
      if (tier.savingsBadge != null) {
        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(50))
              .background(StatusSuccess.copy(alpha = 0.18f))
              .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
          Text(
            text = tier.savingsBadge,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = StatusSuccess,
          )
        }
      }
    }

    Row(
      verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = priceText,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = TextPrimary,
      )
      Text(
        text = tier.billingSuffix,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
      )
    }

    Text(
      text = if (hasTrial) "4-day Play Store trial" else "Billed per ${tier.periodLabel}",
      style = MaterialTheme.typography.labelSmall,
      color = if (selected) primary else TextMuted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun ProBenefitRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  tint: Color,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier =
        Modifier.size(36.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(tint.copy(alpha = 0.14f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(18.dp),
      )
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = TextPrimary,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
      )
    }
  }
}
