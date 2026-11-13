package com.acopy

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

class SplitScreenAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                val source = event.source ?: return
                if (source.isEditable) {
                    lastEditableNodeRef = WeakReference(source)
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        lastEditableNodeRef = null
        instance = null
        super.onDestroy()
    }

    data class EditableFieldInfo(
        val index: Int,
        val label: String,
        val previewText: String,
        val node: AccessibilityNodeInfo
    )

    enum class PasteOutcome {
        PASTED_TO_FIELD,
        NEED_PICK_COLUMN,
        COPIED_ONLY_NO_SERVICE,
        COPIED_ONLY_NO_FIELD
    }

    companion object {
        @Volatile
        var instance: SplitScreenAccessibilityService? = null
            private set

        @Volatile
        private var lastEditableNodeRef: WeakReference<AccessibilityNodeInfo>? = null

        fun isAccessibilityEnabled(context: Context): Boolean {
            val serviceName1 = "${context.packageName}/${SplitScreenAccessibilityService::class.java.canonicalName}"
            val serviceName2 = "${context.packageName}/${SplitScreenAccessibilityService::class.java.name}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(serviceName1, ignoreCase = true) ||
                    componentName.equals(serviceName2, ignoreCase = true) ||
                    componentName.contains("SplitScreenAccessibilityService", ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        fun openAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }

        fun openDeveloperSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }

        fun toggleSplitScreen(): Boolean {
            val service = instance ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                service.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            } else {
                false
            }
        }

        fun openRecents(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_RECENTS)
        }

        fun copyToClipboard(context: Context, text: String) {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("pintasan", text))
            } catch (_: Exception) {}
        }

        fun readFromClipboard(context: Context): String {
            return try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    clip.getItemAt(0).coerceToText(context)?.toString() ?: ""
                } else {
                    ""
                }
            } catch (_: Exception) {
                ""
            }
        }

        fun pasteTextToFocusedField(context: Context, text: String): PasteOutcome {
            copyToClipboard(context, text)
            val service = instance ?: return PasteOutcome.COPIED_ONLY_NO_SERVICE

            // 1. Check currently focused input node across external windows
            val focusedNode = findFocusedEditableNode(service, context.packageName)
            if (focusedNode != null && performPasteOnNode(focusedNode, text)) {
                return PasteOutcome.PASTED_TO_FIELD
            }

            // 2. Check last tracked editable node if still visible & valid
            val lastNode = lastEditableNodeRef?.get()
            if (lastNode != null && lastNode.refresh() && lastNode.isEditable && lastNode.isVisibleToUser) {
                lastNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                if (performPasteOnNode(lastNode, text)) {
                    return PasteOutcome.PASTED_TO_FIELD
                }
            }

            // 3. Check if there are visible editable fields so user can choose a specific column
            val fields = getVisibleEditableFields(context)
            return if (fields.size == 1) {
                if (pasteTextToSpecificField(context, fields[0].node, text)) {
                    PasteOutcome.PASTED_TO_FIELD
                } else {
                    PasteOutcome.COPIED_ONLY_NO_FIELD
                }
            } else if (fields.size > 1) {
                PasteOutcome.NEED_PICK_COLUMN
            } else {
                PasteOutcome.COPIED_ONLY_NO_FIELD
            }
        }

        fun getVisibleEditableFields(context: Context): List<EditableFieldInfo> {
            val service = instance ?: return emptyList()
            val results = mutableListOf<EditableFieldInfo>()
            val seenNodes = mutableSetOf<AccessibilityNodeInfo>()

            val roots = collectExternalWindowRoots(service, context.packageName)
            for (root in roots) {
                collectEditableNodesRecursive(root, results, seenNodes)
            }
            return results
        }

        fun pasteTextToSpecificField(
            context: Context,
            node: AccessibilityNodeInfo,
            text: String
        ): Boolean {
            copyToClipboard(context, text)
            return try {
                node.refresh()
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                lastEditableNodeRef = WeakReference(node)
                performPasteOnNode(node, text)
            } catch (_: Exception) {
                false
            }
        }

        private fun findFocusedEditableNode(
            service: SplitScreenAccessibilityService,
            ownPackage: String
        ): AccessibilityNodeInfo? {
            val roots = collectExternalWindowRoots(service, ownPackage)
            for (root in roots) {
                val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused != null && focused.isEditable && focused.isVisibleToUser) {
                    return focused
                }
            }
            return null
        }

        private fun collectExternalWindowRoots(
            service: SplitScreenAccessibilityService,
            ownPackage: String
        ): List<AccessibilityNodeInfo> {
            val roots = mutableListOf<AccessibilityNodeInfo>()
            try {
                val windows = service.windows
                if (!windows.isNullOrEmpty()) {
                    for (win in windows) {
                        val root = win.root ?: continue
                        val pkg = root.packageName?.toString() ?: ""
                        if (pkg != ownPackage) {
                            roots.add(root)
                        }
                    }
                }
            } catch (_: Exception) {}

            if (roots.isEmpty()) {
                try {
                    val activeRoot = service.rootInActiveWindow
                    if (activeRoot != null && activeRoot.packageName?.toString() != ownPackage) {
                        roots.add(activeRoot)
                    }
                } catch (_: Exception) {}
            }
            return roots
        }

        private fun collectEditableNodesRecursive(
            node: AccessibilityNodeInfo,
            results: MutableList<EditableFieldInfo>,
            seen: MutableSet<AccessibilityNodeInfo>
        ) {
            if (!node.isVisibleToUser) return
            if (node.isEditable && seen.add(node)) {
                val hint = node.hintText?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val text = node.text?.toString()?.trim().orEmpty()
                val idx = results.size + 1
                val label = when {
                    hint.isNotEmpty() -> "Kolom $idx: $hint"
                    desc.isNotEmpty() -> "Kolom $idx: $desc"
                    else -> "Kolom Input $idx"
                }
                val preview = if (text.isNotEmpty()) text else "(kosong)"
                results.add(EditableFieldInfo(idx, label, preview, node))
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectEditableNodesRecursive(child, results, seen)
            }
        }

        private fun performPasteOnNode(node: AccessibilityNodeInfo, text: String): Boolean {
            if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                return true
            }
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }
}
