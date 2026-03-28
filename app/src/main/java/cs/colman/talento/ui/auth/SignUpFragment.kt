package cs.colman.talento.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import cs.colman.talento.R
import cs.colman.talento.databinding.FragmentSignUpBinding
import cs.colman.talento.utils.LoadingUtil
import cs.colman.talento.utils.NetworkResult
import cs.colman.talento.utils.SnackbarType
import cs.colman.talento.utils.UserManager
import cs.colman.talento.utils.ValidationUtil
import cs.colman.talento.utils.applyPhoneFormatting
import cs.colman.talento.utils.showSnackbar

class SignUpFragment : Fragment() {

    private var _binding: FragmentSignUpBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnSignup.setOnClickListener {
            if (validateInput()) {
                signUpUser()
            }
        }

        binding.etPhone.applyPhoneFormatting()

    }

    private fun observeViewModel() {
        viewModel.registerState.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    showLoading(true)
                }
                is NetworkResult.Success -> {
                    showLoading(false)
                    UserManager.saveUserId(requireContext(), result.data)
                    showSnackbar(binding.root, getString(R.string.account_created_successfully), SnackbarType.SUCCESS)
                    findNavController().navigate(R.id.action_signup_to_home)
                }
                is NetworkResult.Error -> {
                    showLoading(false)
                    showSnackbar(binding.root, result.message, SnackbarType.ERROR)
                }
            }
        }
    }

    private fun signUpUser() {
        val fullName = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        viewModel.registerUser(fullName, email, phone, password)
    }

    private fun validateInput(): Boolean {
        val fullName = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (!ValidationUtil.isValidName(fullName)) {
            binding.etName.error = getString(R.string.full_name_required)
            return false
        }

        if (!ValidationUtil.isValidEmail(email)) {
            binding.etEmail.error = getString(R.string.valid_email_required)
            return false
        }

        if (!ValidationUtil.isValidPhoneNumber(phone)) {
            binding.etPhone.error = getString(R.string.valid_phone_required)
            return false
        }

        if (!ValidationUtil.isValidPassword(password)) {
            binding.etPassword.error = getString(R.string.password_length_error)
            return false
        }

        return true
    }

    private fun showLoading(isLoading: Boolean) {
        binding.btnSignup.isEnabled = !isLoading
        binding.btnSignup.text = if (isLoading) getString(R.string.signing_up) else getString(R.string.sign_up)
        LoadingUtil.showLoading(requireContext(), isLoading)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
