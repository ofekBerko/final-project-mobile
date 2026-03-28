package cs.colman.talento.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import cs.colman.talento.R
import cs.colman.talento.databinding.FragmentLoginBinding
import cs.colman.talento.utils.LoadingUtil
import cs.colman.talento.utils.NetworkResult
import cs.colman.talento.utils.SnackbarType
import cs.colman.talento.utils.UserManager
import cs.colman.talento.utils.ValidationUtil
import cs.colman.talento.utils.showSnackbar

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnSignup.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_signup)
        }

        binding.btnLogin.setOnClickListener {
            if (validateInput()) {
                loginUser()
            }
        }
    }

    private fun loginUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        viewModel.loginUser(email, password)
    }

    private fun validateInput(): Boolean {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (!ValidationUtil.isValidEmail(email)) {
            binding.etEmail.error = getString(R.string.valid_email_required)
            return false
        }

        if (!ValidationUtil.isValidPassword(password)) {
            binding.etPassword.error = getString(R.string.password_length_error)
            return false
        }

        return true
    }


    private fun observeViewModel() {
        viewModel.loginState.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    showLoading(true)
                }
                is NetworkResult.Success -> {
                    showLoading(false)
                    UserManager.saveUserId(requireContext(), result.data)
                    showSnackbar(binding.root, getString(R.string.login_successful), SnackbarType.SUCCESS)
                    findNavController().navigate(R.id.action_login_to_home)
                }
                is NetworkResult.Error -> {
                    showLoading(false)
                    showSnackbar(binding.root, result.message, SnackbarType.ERROR)
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.btnLogin.isEnabled = !isLoading
        binding.btnLogin.text = if (isLoading) getString(R.string.logging_in) else getString(R.string.login)
        LoadingUtil.showLoading(requireContext(), isLoading)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
