package com.thiagoperea.gamemode.internal

import android.app.NotificationManager
import android.os.Build

// Para apps, o Android 15+ aplica a política na regra de DND do próprio app, então
// precisa ser definida antes de ligar o DND. Sem isso, as notificações já na bandeja
// continuam visíveis (a HyperOS não expõe essa opção na tela de DND).
fun NotificationManager.setNotificationListHidden(hidden: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

    val policy = notificationPolicy
    val flag = NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST
    val effects = if (hidden) {
        policy.suppressedVisualEffects or flag
    } else {
        policy.suppressedVisualEffects and flag.inv()
    }
    if (effects == policy.suppressedVisualEffects) return

    val updated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        NotificationManager.Policy(
            policy.priorityCategories,
            policy.priorityCallSenders,
            policy.priorityMessageSenders,
            effects,
            policy.priorityConversationSenders
        )
    } else {
        NotificationManager.Policy(
            policy.priorityCategories,
            policy.priorityCallSenders,
            policy.priorityMessageSenders,
            effects
        )
    }
    notificationPolicy = updated
}
