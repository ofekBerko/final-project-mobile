package cs.colman.talento.utils

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

class PhoneFormattingTextWatcher : TextWatcher {
    private var isFormatting = false

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        if (isFormatting) return
        isFormatting = true

        val text = s.toString().replace("\\D".toRegex(), "")
        val formatted = StringBuilder()

        for (i in text.indices) {
            if (i == 3 || i == 6) formatted.append("-")
            formatted.append(text[i])
            if (i >= 9) break
        }

        s?.clear()
        s?.append(formatted)

        isFormatting = false
    }
}

fun EditText.applyPhoneFormatting() {
    this.addTextChangedListener(PhoneFormattingTextWatcher())
}
