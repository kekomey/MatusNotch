package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.MatusNotchApplication
import com.example.model.ActiveCallInfo
import com.example.model.ActiveNotificationBanner
import com.example.system.SystemBridges

class NotchMediaListenerService : NotificationListenerService() {

  private var sessionManager: MediaSessionManager? = null
  private var observedController: MediaController? = null

  private val sessionsListener =
    MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
      attachPrimaryController(controllers)
    }

  private val controllerCallback =
    object : MediaController.Callback() {
      override fun onPlaybackStateChanged(state: PlaybackState?) {
        syncFromController(observedController)
      }

      override fun onMetadataChanged(metadata: MediaMetadata?) {
        syncFromController(observedController)
      }
    }

  override fun onListenerConnected() {
    super.onListenerConnected()
    val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    sessionManager = manager
    val component = ComponentName(this, NotchMediaListenerService::class.java)
    runCatching {
      manager?.addOnActiveSessionsChangedListener(sessionsListener, component)
      attachPrimaryController(manager?.getActiveSessions(component))
    }
    syncOngoingSystemStatuses()
  }

  override fun onListenerDisconnected() {
    super.onListenerDisconnected()
    runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
    detachController()
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    super.onNotificationPosted(sbn)
    val posted = sbn ?: return
    if (posted.packageName == packageName) return
    val notification = posted.notification ?: return

    syncOngoingSystemStatuses()

    if (posted.isOngoing) return
    if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
    if ((notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return

    val extras = notification.extras
    // Skip pure media transport updates (handled via MediaController)
    if (
      notification.category == Notification.CATEGORY_TRANSPORT ||
      extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
    ) {
      return
    }

    val pkg = posted.packageName.orEmpty()
    val app = applicationContext as? MatusNotchApplication ?: return
    if (!app.coordinator.shouldPopupNotification(pkg)) return

    val appName = SystemBridges.loadAppLabel(this, pkg).ifBlank { "Notification" }
    val title =
      extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        ?: extras?.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        ?: appName

    val body =
      extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        ?: extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        ?: extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()

    if (title.isBlank() && body.isBlank()) return

    val appDrawable =
      SystemBridges.loadAppIcon(this, pkg)
        ?: runCatching { notification.smallIcon?.loadDrawable(this) }.getOrNull()
    val appIcon = SystemBridges.drawableToScaledImageBitmap(appDrawable, maxDim = 96)

    val postedTime = posted.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
    app.coordinator.onNotificationPosted(
      ActiveNotificationBanner(
        packageName = pkg,
        appName = appName,
        appIcon = appIcon,
        title = title,
        body = body,
        timestampMillis = postedTime,
        contentIntent = notification.contentIntent,
      )
    )
  }

  override fun onNotificationRemoved(sbn: StatusBarNotification?) {
    super.onNotificationRemoved(sbn)
    syncOngoingSystemStatuses()
  }

  private fun syncOngoingSystemStatuses() {
    val app = applicationContext as? MatusNotchApplication ?: return
    val activeList = runCatching { activeNotifications?.toList().orEmpty() }.getOrDefault(emptyList())

    val callSbn =
      activeList.firstOrNull { sbn ->
        sbn.packageName != packageName && sbn.notification?.category == Notification.CATEGORY_CALL
      }
    val callInfo =
      callSbn?.let { sbn ->
        val extras = sbn.notification?.extras
        val pkg = sbn.packageName.orEmpty()
        val caller =
          extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: SystemBridges.loadAppLabel(this, pkg)
        val sub =
          extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: "Active call"
        ActiveCallInfo(
          key = sbn.key.orEmpty(),
          callerName = caller,
          subtitle = sub,
          startTimeMillis = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis(),
          contentIntent = sbn.notification?.contentIntent,
          packageName = pkg,
        )
      }
    app.coordinator.updateOngoingCall(callInfo)

    val recSbn =
      activeList.firstOrNull { sbn ->
        if (sbn.packageName == packageName) return@firstOrNull false
        val notif = sbn.notification ?: return@firstOrNull false
        val pkg = sbn.packageName.orEmpty().lowercase()
        val channel = notif.channelId.orEmpty().lowercase()
        val extras = notif.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().lowercase()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().lowercase()
        val isRecText =
          title.contains("recording screen") ||
            title.contains("screen recording") ||
            text.contains("recording screen") ||
            channel.contains("screenrecord") ||
            channel.contains("screen_record")
        val isRecPkg = pkg == "com.android.systemui" || pkg.contains("screenrecord")
        sbn.isOngoing && isRecText && (isRecPkg || channel.contains("record"))
      }
    val virtualDisplayRecording = SystemBridges.isVirtualScreenCaptureDisplayActive(this)
    app.coordinator.updateScreenRecording(
      active = recSbn != null || virtualDisplayRecording,
      startMillis = recSbn?.postTime ?: 0L,
    )
  }

  private fun attachPrimaryController(controllers: List<MediaController>?) {
    detachController()
    val preferred =
      controllers?.firstOrNull {
        it.playbackState?.state == PlaybackState.STATE_PLAYING
      } ?: controllers?.firstOrNull()

    observedController = preferred
    activeController = preferred
    preferred?.registerCallback(controllerCallback)
    syncFromController(preferred)
  }

  private fun detachController() {
    observedController?.unregisterCallback(controllerCallback)
    observedController = null
    activeController = null
  }

  private fun syncFromController(controller: MediaController?) {
    val app = applicationContext as? MatusNotchApplication ?: return
    if (controller == null) {
      app.coordinator.updateExternalMediaSession(
        isPlaying = false,
        title = "No active audio",
        artistAndWriters = "Play music or tap Play",
        sourceApp = "",
        albumArt = null,
        hasListenerAccess = true,
      )
      return
    }

    val meta = controller.metadata
    val title =
      meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
        ?: meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()

    val artist =
      meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
        ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()

    val writers =
      listOfNotNull(
        meta?.getString(MediaMetadata.METADATA_KEY_WRITER)?.takeIf { it.isNotBlank() },
        meta?.getString(MediaMetadata.METADATA_KEY_COMPOSER)?.takeIf { it.isNotBlank() },
        meta?.getString(MediaMetadata.METADATA_KEY_AUTHOR)?.takeIf { it.isNotBlank() },
      ).distinct().filterNot { it.equals(artist, ignoreCase = true) }.joinToString(", ")

    val pkg = controller.packageName.orEmpty()
    val appLabel = if (pkg.isNotBlank()) SystemBridges.loadAppLabel(this, pkg) else ""

    val combinedCredits =
      when {
        artist.isNotBlank() && writers.isNotBlank() -> "$artist • $writers"
        artist.isNotBlank() -> artist
        writers.isNotBlank() -> writers
        appLabel.isNotBlank() -> appLabel
        else -> "Connected Audio"
      }

    val artBitmap = extractScaledAlbumArt(meta)
    val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING

    app.coordinator.updateExternalMediaSession(
      isPlaying = isPlaying,
      title = title,
      artistAndWriters = combinedCredits,
      sourceApp = appLabel,
      albumArt = artBitmap,
      hasListenerAccess = true,
    )
  }

  private fun extractScaledAlbumArt(meta: MediaMetadata?): ImageBitmap? {
    if (meta == null) return null
    val rawBitmap =
      meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
        ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        ?: run {
          val uriStr =
            meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
              ?: meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
              ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
          uriStr?.let { str ->
            runCatching {
              contentResolver.openInputStream(Uri.parse(str))?.use { stream ->
                BitmapFactory.decodeStream(stream)
              }
            }.getOrNull()
          }
        }
        ?: return null

    return runCatching {
      val maxDim = 160
      val scaled =
        if (rawBitmap.width > maxDim || rawBitmap.height > maxDim) {
          val ratio = minOf(maxDim.toFloat() / rawBitmap.width, maxDim.toFloat() / rawBitmap.height)
          Bitmap.createScaledBitmap(
            rawBitmap,
            (rawBitmap.width * ratio).toInt().coerceAtLeast(1),
            (rawBitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
          )
        } else {
          rawBitmap
        }
      scaled.asImageBitmap()
    }.getOrNull()
  }

  companion object {
    @Volatile
    var activeController: MediaController? = null
      private set
  }
}
