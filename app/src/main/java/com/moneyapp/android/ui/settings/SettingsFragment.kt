package com.moneyapp.android.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import com.moneyapp.android.BuildConfig
import com.moneyapp.android.databinding.FragmentSettingsBinding
import com.moneyapp.android.ui.MainViewModel
import com.moneyapp.android.update.UpdateChecker
import kotlinx.coroutines.launch
import android.util.Log
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    // 🧩 MainActivity ile ortak ViewModel
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvVersionInfo.text =
            "Sürüm: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        // 🔄 Senkronize Et
        binding.btnSyncNow.setOnClickListener {
            Toast.makeText(requireContext(), "🔄 Sunucu ile senkron başlatılıyor...", Toast.LENGTH_SHORT).show()
            viewModel.syncWithServer() // ✅ Asıl işlem burada
            Log.e("TEST", "🟢 btnSyncNow tıklandı!") // 💥 Deneme logu
        }

        // 🆕 Güncelleme kontrolü
        binding.btnCheckUpdate.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                UpdateChecker.checkAndPrompt(requireContext())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
