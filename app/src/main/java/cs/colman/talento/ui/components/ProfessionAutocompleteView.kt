package cs.colman.talento.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import cs.colman.talento.data.model.Profession
import cs.colman.talento.databinding.ViewProfessionAutocompleteBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProfessionAutocompleteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewProfessionAutocompleteBinding =
        ViewProfessionAutocompleteBinding.inflate(LayoutInflater.from(context), this)

    private var searchJob: Job? = null
    private var onSearchCallback: ((query: String, limit: Int) -> Unit)? = null
    private var professionAdapter: ArrayAdapter<String>? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var textChangeListeners = mutableListOf<(String) -> Unit>()

    fun setup(
        lifecycleOwner: LifecycleOwner,
        professions: LiveData<List<Profession>>,
        onSearch: (query: String, limit: Int) -> Unit
    ) {
        this.lifecycleOwner = lifecycleOwner
        this.onSearchCallback = onSearch

        setupAutocomplete()
        observeProfessions(lifecycleOwner, professions)
    }

    fun setError(error: String?) {
        binding.layoutProfession.error = error
    }

    fun getText(): String {
        return binding.etProfession.text.toString().trim()
    }

    fun setText(text: String) {
        binding.etProfession.setText(text)
    }

    fun isProfessionValid(professionName: String, professions: List<Profession>?): Boolean {
        if (professionName.isEmpty()) return false
        return professions?.any { it.name.equals(professionName, ignoreCase = true) } == true
    }

    fun addTextChangeListener(listener: (String) -> Unit) {
        textChangeListeners.add(listener)
    }

    fun setOnItemClickListener(listener: (String) -> Unit) {
        binding.etProfession.setOnItemClickListener { _, _, _, _ ->
            binding.etProfession.requestFocus()
            val selectedText = binding.etProfession.text.toString()
            listener(selectedText)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupAutocomplete() {
        professionAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_dropdown_item_1line,
            ArrayList<String>()
        )
        binding.etProfession.setAdapter(professionAdapter)
        binding.etProfession.threshold = 0

        binding.etProfession.setOnClickListener {
            if (professionAdapter?.count == 0) {
                onSearchCallback?.invoke("", 15)
            }

            binding.etProfession.postDelayed({
                if (binding.etProfession.hasFocus()) {
                    binding.etProfession.showDropDown()
                }
            }, 100)
        }

        binding.etProfession.setOnTouchListener { v, _ ->
            val adapter = binding.etProfession.adapter
            if (adapter != null && adapter.count > 0) {
                v.performClick()
                binding.etProfession.showDropDown()
            }
            false
        }

        binding.etProfession.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()

                lifecycleOwner?.let { owner ->
                    searchJob = owner.lifecycleScope.launch {
                        delay(150)
                        val query = s?.toString() ?: ""
                        onSearchCallback?.invoke(query, 15)

                        textChangeListeners.forEach { listener ->
                            listener(query)
                        }
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etProfession.setOnItemClickListener { _, _, _, _ ->
            binding.etProfession.requestFocus()
            val selectedText = binding.etProfession.text.toString()
            textChangeListeners.forEach { listener ->
                listener(selectedText)
            }
        }
    }

    private fun observeProfessions(lifecycleOwner: LifecycleOwner, professions: LiveData<List<Profession>>) {
        professions.observe(lifecycleOwner) { professionsList ->
            val professionNames = professionsList.map { it.name }
            if (professionNames.isNotEmpty() || professionAdapter?.isEmpty == true) {
                professionAdapter?.clear()
                professionAdapter?.addAll(professionNames)
                professionAdapter?.notifyDataSetChanged()
                if (binding.etProfession.hasFocus() && professionNames.isNotEmpty()) {
                    binding.etProfession.post {
                        binding.etProfession.showDropDown()
                    }
                }
            }
        }
    }
}
