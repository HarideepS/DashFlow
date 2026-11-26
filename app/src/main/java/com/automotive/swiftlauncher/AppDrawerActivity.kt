package com.automotive.swiftlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.automotive.swiftlauncher.databinding.ActivityAppDrawerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppDrawerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDrawerBinding
    private lateinit var adapter: AppAdapter
    private var isPickerMode = false

    companion object {
        const val ACTION_PICK_APP = "com.automotive.swiftlauncher.PICK_APP"
        const val EXTRA_SELECTED_PACKAGE = "extra_selected_package"
        const val EXTRA_SELECTED_LABEL = "extra_selected_label"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(PreferenceHelper.wrapWithCustomDpi(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()

        binding = ActivityAppDrawerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isPickerMode = (intent.action == ACTION_PICK_APP)
        if (isPickerMode) {
            binding.editSearch.hint = getString(R.string.rebind_prompt)
        }

        setupRecyclerView()
        setupListeners()
        loadInstalledApps()
    }

    private fun setupRecyclerView() {
        adapter = AppAdapter(emptyList()) { app ->
            if (isPickerMode) {
                val data = Intent().apply {
                    putExtra(EXTRA_SELECTED_PACKAGE, app.packageName)
                    putExtra(EXTRA_SELECTED_LABEL, app.label)
                }
                setResult(RESULT_OK, data)
                finish()
            } else {
                launchApp(app.packageName)
            }
        }

        // 5 columns in landscape for high-density touch targets
        binding.recyclerApps.layoutManager = GridLayoutManager(this, 5)
        binding.recyclerApps.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnCloseDrawer.setOnClickListener {
            finish()
        }

        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadInstalledApps() {
        lifecycleScope.launch {
            val appList = withContext(Dispatchers.IO) {
                val pm = packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                val list = ArrayList<AppInfo>()

                for (info in resolveInfos) {
                    val pkg = info.activityInfo.packageName
                    // Exclude launcher itself from drawer
                    if (pkg == packageName) continue

                    val label = info.loadLabel(pm).toString()
                    val icon = info.loadIcon(pm)
                    list.add(AppInfo(label, pkg, info.activityInfo.name, icon))
                }
                list.sort()
                list
            }
            adapter.updateData(appList)
        }
    }

    private fun launchApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            finish()
        }
    }

    private fun hideSystemUI() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }
}
