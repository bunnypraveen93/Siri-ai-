package com.praveen.siriai

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.TextView

/**
 * Builds the small custom-styled popups used by the chat history list:
 * the long-press action menu (Pin / Rename / Delete) and the two dialogs
 * it opens. Kept separate from MainActivity so the UI-building boilerplate
 * doesn't clutter the Activity's actual logic.
 */
object DialogHelper {

    private const val POPUP_BG_COLOR = "#2A2B32"
    private const val DIALOG_WIDTH_RATIO = 0.90

    /** The long-press popup on a chat history item: Pin/Unpin, Rename, Delete. */
    fun showChatItemMenu(
        context: Context,
        anchorView: View,
        parentLayout: RelativeLayout,
        isPinned: Boolean,
        onPin: () -> Unit,
        onRename: () -> Unit,
        onDelete: () -> Unit
    ) {
        val popupView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor(POPUP_BG_COLOR))
                cornerRadius = 32f
            }
            setPadding(16, 24, 16, 24)
        }

        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 20f
        }

        fun addMenuItem(iconRes: Int, label: String, color: Int, action: () -> Unit) {
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 24, 64, 24)
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
            }

            val icon = ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(color)
                layoutParams = LinearLayout.LayoutParams(48, 48)
            }

            val labelView = TextView(context).apply {
                text = label
                setTextColor(color)
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.leftMargin = 32 }
            }

            item.addView(icon)
            item.addView(labelView)
            item.setOnClickListener {
                action()
                popupWindow.dismiss()
            }
            popupView.addView(item)
        }

        addMenuItem(R.drawable.ic_pin_outline, if (isPinned) "Unpin" else "Pin", Color.WHITE) { onPin() }
        addMenuItem(R.drawable.ic_edit_outline, "Rename", Color.WHITE) { onRename() }
        addMenuItem(R.drawable.ic_delete_outline, "Delete", Color.parseColor("#FF5252")) { onDelete() }

        popupWindow.setOnDismissListener { parentLayout.removeView(anchorView) }
        popupWindow.showAsDropDown(anchorView, 0, 0)
    }

    /** Prompts for a new chat title, pre-filled with [currentTitle]. */
    fun showRenameDialog(context: Context, currentTitle: String, onRename: (String) -> Unit) {
        val dialog = buildBaseDialog(context)

        val container = dialogContainer(context, paddingAll = 48)

        val boxLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setStroke(3, Color.parseColor("#666666"))
                cornerRadius = 16f
            }
            setPadding(32, 16, 32, 16)
        }

        boxLayout.addView(TextView(context).apply {
            text = "New name"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 12f
        })

        val input = EditText(context).apply {
            setText(currentTitle)
            setTextColor(Color.WHITE)
            background = null
            setPadding(0, 8, 0, 8)
            textSize = 16f
            setSingleLine(true)
        }
        boxLayout.addView(input)
        container.addView(boxLayout)

        val renameBtn = actionText(context, "Rename", Color.parseColor("#888888"))
        val cancelBtn = actionText(context, "Cancel", Color.WHITE)
        container.addView(actionRow(cancelBtn, renameBtn))

        dialog.setContentView(container)
        sizeDialog(context, dialog)

        cancelBtn.setOnClickListener { dialog.dismiss() }
        input.selectAll()
        input.requestFocus()

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renameBtn.setTextColor(
                    if (s.toString().trim().isNotEmpty()) Color.WHITE else Color.parseColor("#888888")
                )
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        renameBtn.setOnClickListener {
            val newTitle = input.text.toString().trim()
            if (newTitle.isNotEmpty()) {
                onRename(newTitle)
                dialog.dismiss()
            }
        }

        dialog.show()
        input.postDelayed({
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    /** Confirms permanent deletion of a chat. */
    fun showDeleteDialog(context: Context, onConfirm: () -> Unit) {
        val dialog = buildBaseDialog(context)
        val container = dialogContainer(context, paddingAll = 64, paddingBottom = 48)

        container.addView(TextView(context).apply {
            text = "Delete Chat"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
        })

        container.addView(TextView(context).apply {
            text = "Do you confirm this delete permanently?"
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 32 }
        })

        val deleteBtn = actionText(context, "Delete", Color.parseColor("#FF5252"))
        val cancelBtn = actionText(context, "Cancel", Color.WHITE)
        container.addView(actionRow(cancelBtn, deleteBtn))

        dialog.setContentView(container)
        sizeDialog(context, dialog)

        cancelBtn.setOnClickListener { dialog.dismiss() }
        deleteBtn.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }

        dialog.show()
    }

    /** Confirms making a phone call, handling actions in English. */
    fun showCallConfirmDialog(
        context: Context, 
        promptMessage: String, 
        onConfirm: () -> Unit, 
        onCancel: () -> Unit
    ) {
        val dialog = buildBaseDialog(context)
        val container = dialogContainer(context, paddingAll = 64, paddingBottom = 48)

        container.addView(TextView(context).apply {
            text = "Confirm Call"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
        })

        container.addView(TextView(context).apply {
            text = promptMessage
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 32 }
        })

        val callBtn = actionText(context, "Call", Color.parseColor("#4CAF50"))
        val cancelBtn = actionText(context, "Cancel", Color.WHITE)
        container.addView(actionRow(cancelBtn, callBtn))

        dialog.setContentView(container)
        sizeDialog(context, dialog)

        cancelBtn.setOnClickListener { 
            onCancel()
            dialog.dismiss() 
        }
        
        callBtn.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }

        dialog.show()
    }

    // --- Shared dialog-building helpers (keep the dialogs short and consistent) ---

    private fun buildBaseDialog(context: Context): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    private fun dialogContainer(context: Context, paddingAll: Int, paddingBottom: Int = paddingAll): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor(POPUP_BG_COLOR))
                cornerRadius = 48f
            }
            setPadding(paddingAll, paddingAll, paddingAll, paddingBottom)
        }
    }

    private fun actionText(context: Context, label: String, color: Int): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(color)
            textSize = 14f
            setPadding(32, 16, 32, 16)
            setTypeface(null, Typeface.BOLD)
        }
    }

    private fun actionRow(cancelBtn: TextView, confirmBtn: TextView): LinearLayout {
        return LinearLayout(cancelBtn.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 48 }
            addView(cancelBtn)
            addView(confirmBtn)
        }
    }

    private fun sizeDialog(context: Context, dialog: Dialog) {
        val params = dialog.window?.attributes
        params?.width = (context.resources.displayMetrics.widthPixels * DIALOG_WIDTH_RATIO).toInt()
        dialog.window?.attributes = params
    }
}