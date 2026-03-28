package cs.colman.talento.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import cs.colman.talento.R

enum class SnackbarType {
    SUCCESS, ERROR, WARNING
}

fun showSnackbar(view: View, message: String, type: SnackbarType) {
    val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)

    val color = when (type) {
        SnackbarType.SUCCESS -> view.context.getColor(R.color.green)
        SnackbarType.ERROR -> view.context.getColor(R.color.red)
        SnackbarType.WARNING -> view.context.getColor(R.color.orange)
    }

    snackbar.setBackgroundTint(color)

    snackbar.setActionTextColor(Color.LTGRAY)

    snackbar.setAction(view.context.getString(R.string.dismiss)) { snackbar.dismiss() }

    val textView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
    textView?.setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body2)

    snackbar.show()
}

object LoadingUtil {
    private var loadingDialog: Dialog? = null

    fun showLoading(context: Context, isLoading: Boolean) {
        if (isLoading) {
            if (loadingDialog == null) {
                loadingDialog = Dialog(context).apply {
                    setContentView(R.layout.dialog_loading)
                    window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    setCancelable(false)
                }
            }
            if (!loadingDialog!!.isShowing) {
                loadingDialog?.show()
            }
        } else {
            loadingDialog?.dismiss()
            loadingDialog = null
        }
    }
}
