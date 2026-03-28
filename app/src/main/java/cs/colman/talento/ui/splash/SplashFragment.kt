package cs.colman.talento.ui.splash

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import cs.colman.talento.R
import cs.colman.talento.databinding.FragmentSplashBinding
import cs.colman.talento.utils.LoadingUtil

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SplashViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeViewModel()
        viewModel.checkUserLoginStatus()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            LoadingUtil.showLoading(requireContext(), isLoading)
        }

        viewModel.isUserLoggedIn.observe(viewLifecycleOwner) { isLoggedIn ->
            if (isLoggedIn) {
                findNavController().navigate(R.id.action_splash_to_home)
            } else {
                findNavController().navigate(R.id.action_splash_to_login)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
