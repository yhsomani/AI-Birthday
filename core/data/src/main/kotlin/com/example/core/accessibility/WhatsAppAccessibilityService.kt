package com.example.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.ConcurrentLinkedQueue

class WhatsAppAccessibilityService : AccessibilityService() {
    companion object {
        val pendingQueue: ConcurrentLinkedQueue<WhatsAppSendJob> = ConcurrentLinkedQueue()
        var instance: WhatsAppAccessibilityService? = null
    }

    data class WhatsAppSendJob(
        val phoneNumber: String,
        val message: String,
        val eventId: String,
        val onComplete: (Boolean) -> Unit
    )

    private var currentJob: WhatsAppSendJob? = null
    private var sendState: SendState = SendState.IDLE
    private var jobTimeoutRunnable: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    enum class SendState {
        IDLE, OPENING_CHAT, WAITING_FOR_CHAT, TYPING_MESSAGE, WAITING_FOR_SEND_BUTTON, DONE
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            packageNames = arrayOf("com.whatsapp", "com.whatsapp.w4b")
        }
    }

    fun enqueueSend(job: WhatsAppSendJob) {
        pendingQueue.add(job)
        processNextIfIdle()
    }

    private fun processNextIfIdle() {
        if (sendState != SendState.IDLE) return
        currentJob = pendingQueue.poll() ?: return
        openWhatsAppChat(currentJob!!.phoneNumber)
    }

    private fun openWhatsAppChat(phone: String) {
        sendState = SendState.OPENING_CHAT
        if (isDeviceLocked()) {
            failJob("Device is locked; cannot safely send WhatsApp message (would unlock screen).")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/$phone")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        jobTimeoutRunnable = Runnable {
            if (sendState != SendState.DONE) {
                currentJob?.onComplete?.invoke(false)
                currentJob = null
                sendState = SendState.IDLE
                performGlobalAction(GLOBAL_ACTION_BACK)
                mainHandler.postDelayed({ processNextIfIdle() }, 1000L)
            }
        }
        mainHandler.postDelayed(jobTimeoutRunnable!!, 15000L) // 15s timeout
        
        applicationContext.startActivity(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val job = currentJob ?: return

        when (sendState) {
            SendState.OPENING_CHAT -> {
                if (event.packageName?.toString()?.startsWith("com.whatsapp") == true &&
                    event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    sendState = SendState.WAITING_FOR_CHAT
                    mainHandler.postDelayed({
                        typeMessage(job.message)
                    }, 1500L)
                }
            }
            else -> {}
        }
    }

    private fun typeMessage(message: String) {
        sendState = SendState.TYPING_MESSAGE
        val root = rootInActiveWindow ?: run {
            failJob("Root view not available")
            return
        }
        val inputField = findMessageInputField(root)
        if (inputField != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
            }
            inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            
            // Post verification check after 500ms
            mainHandler.postDelayed({
                verifyTextTyped(message)
            }, 500L)
        } else {
            failJob("Input field not found")
        }
    }

    private fun verifyTextTyped(message: String) {
        val root = rootInActiveWindow ?: run {
            failJob("Root view not available during text verification")
            return
        }
        val inputField = findMessageInputField(root)
        if (inputField == null) {
            failJob("Input field disappeared during text verification")
            return
        }
        val text = inputField.text?.toString() ?: ""
        if (text != message) {
            failJob("Text mismatch: expected '$message', got '$text'")
            return
        }

        val sendBtn = findSendButton(root)
        if (sendBtn != null) {
            sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            sendState = SendState.WAITING_FOR_SEND_BUTTON
            verifySendCompleted(0)
        } else {
            failJob("Send button not found after typing")
        }
    }

    private fun verifySendCompleted(attempt: Int) {
        if (sendState != SendState.WAITING_FOR_SEND_BUTTON) return
        val root = rootInActiveWindow
        val sendBtn = findSendButton(root)
        if (sendBtn == null) {
            completeJobSuccess()
        } else {
            if (attempt >= 6) { // 6 * 500ms = 3s timeout
                failJob("Send button failed to disappear (message send timed out)")
            } else {
                mainHandler.postDelayed({
                    verifySendCompleted(attempt + 1)
                }, 500L)
            }
        }
    }

    private fun failJob(reason: String) {
        jobTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        val job = currentJob
        if (job != null) {
            job.onComplete(false)
            currentJob = null
        }
        sendState = SendState.IDLE
        performGlobalAction(GLOBAL_ACTION_BACK)
        mainHandler.postDelayed({ processNextIfIdle() }, 1000L)
    }

    private fun completeJobSuccess() {
        jobTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        val job = currentJob
        if (job != null) {
            job.onComplete(true)
            currentJob = null
        }
        sendState = SendState.IDLE
        performGlobalAction(GLOBAL_ACTION_BACK)
        mainHandler.postDelayed({ processNextIfIdle() }, 1000L)
    }

    private fun findMessageInputField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val ids = listOf(
            "com.whatsapp:id/entry",
            "com.whatsapp:id/conversation_entry",
            "com.whatsapp.w4b:id/entry"
        )
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) return nodes[0]
        }
        return findEditTextInTree(root)
    }

    private fun findSendButton(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        root ?: return null
        val ids = listOf("com.whatsapp:id/send", "com.whatsapp:id/send_container", "com.whatsapp.w4b:id/send")
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) return nodes[0]
        }
        return null
    }

    private fun findEditTextInTree(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.toString() == "android.widget.EditText" && node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findEditTextInTree(node.getChild(i) ?: continue)
            if (found != null) return found
        }
        return null
    }

    override fun onInterrupt() { instance = null }
    override fun onDestroy() { super.onDestroy(); instance = null }

    private fun isDeviceLocked(): Boolean {
        val km = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return km.isKeyguardLocked
    }
}
