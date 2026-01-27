/*
 * Aurora Store
 *  Copyright (C) 2021, Rahul Kumar Patel <whyorean@gmail.com>
 *  Copyright (C) 2026, Modified for Device Owner support
 *
 *  Aurora Store is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  Aurora Store is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Aurora Store.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.aurora.store.view.ui.preferences.installation

import android.app.admin.DeviceAdminInfo
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.getSystemService
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.aurora.extensions.showDialog
import com.aurora.extensions.toast
import com.aurora.store.R
import com.aurora.store.data.receiver.DeviceOwnerReceiver
import com.aurora.store.util.Preferences.PREFERENCE_INSTALLER_ID
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class InstallationPreference : PreferenceFragmentCompat() {

    companion object {
        private const val TAG = "InstallationPreference"
        private const val PREFERENCE_INSTALLATION_DEVICE_OWNER_CLEAR = "PREFERENCE_INSTALLATION_DEVICE_OWNER_CLEAR"
        private const val PREFERENCE_INSTALLATION_DEVICE_OWNER_TRANSFER = "PREFERENCE_INSTALLATION_DEVICE_OWNER_TRANSFER"
        private const val PREFERENCE_CATEGORY_DEVICE_OWNER = "PREFERENCE_CATEGORY_DEVICE_OWNER"
    }

    data class DeviceAdminApp(
        val info: DeviceAdminInfo,
        val label: String,
        val packageName: String,
        val component: ComponentName,
        val icon: Drawable?
    )

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_installation, rootKey)

        findPreference<Preference>(PREFERENCE_INSTALLER_ID)?.apply {
            setOnPreferenceClickListener {
                findNavController().navigate(R.id.installerFragment)
                true
            }
        }

        setupDeviceOwnerPreferences()
    }

    private fun setupDeviceOwnerPreferences() {
        val packageName = requireContext().packageName
        val devicePolicyManager = requireContext().getSystemService<DevicePolicyManager>()
        val isDeviceOwner = devicePolicyManager?.isDeviceOwnerApp(packageName) ?: false

        findPreference<PreferenceCategory>(PREFERENCE_CATEGORY_DEVICE_OWNER)?.isVisible = isDeviceOwner

        findPreference<Preference>(PREFERENCE_INSTALLATION_DEVICE_OWNER_CLEAR)?.apply {
            isVisible = isDeviceOwner
            setOnPreferenceClickListener {
                context.showDialog(
                    context.getString(R.string.pref_clear_device_owner_title),
                    context.getString(R.string.pref_clear_device_owner_desc),
                    { _: DialogInterface, _: Int ->
                        try {
                            @Suppress("DEPRECATION")
                            devicePolicyManager!!.clearDeviceOwnerApp(packageName)
                            context.toast(R.string.device_owner_removed_success)
                            activity?.recreate()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to clear device owner: ${e.message}")
                            context.toast(R.string.device_owner_removed_failed)
                        }
                    },
                    { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                )
                true
            }
        }

        findPreference<Preference>(PREFERENCE_INSTALLATION_DEVICE_OWNER_TRANSFER)?.apply {
            isVisible = isDeviceOwner && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            setOnPreferenceClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    showTransferDialog(devicePolicyManager!!, packageName)
                }
                true
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun showTransferDialog(devicePolicyManager: DevicePolicyManager, currentPackageName: String) {
        val availableApps = getDeviceAdminApps(currentPackageName)

        if (availableApps.isEmpty()) {
            requireContext().toast("No apps with Device Admin Receiver found")
            return
        }

        val adapter = AppListAdapter(requireContext(), availableApps)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.transfer_device_owner)
            .setAdapter(adapter) { _, which ->
                val selectedApp = availableApps[which]
                confirmAndTransfer(devicePolicyManager, currentPackageName, selectedApp)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun confirmAndTransfer(
        devicePolicyManager: DevicePolicyManager,
        currentPackageName: String,
        targetApp: DeviceAdminApp
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.transfer_device_owner)
            .setMessage("Transfer Device Owner to ${targetApp.label}?")
            .setPositiveButton(R.string.transfer) { _, _ ->
                transferDeviceOwner(devicePolicyManager, currentPackageName, targetApp)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun getDeviceAdminApps(currentPackageName: String): List<DeviceAdminApp> {
        val packageManager = requireContext().packageManager
        val intent = Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED)
        
        return try {
            packageManager.queryBroadcastReceivers(
                intent,
                PackageManager.GET_META_DATA
            ).mapNotNull { resolveInfo ->
                try {
                    val adminInfo = DeviceAdminInfo(requireContext(), resolveInfo)
                    val component = adminInfo.component
                    val appInfo = resolveInfo.activityInfo.applicationInfo
                    
                    if (component.packageName == currentPackageName) return@mapNotNull null
                    
                    val label = try {
                        appInfo.loadLabel(packageManager).toString()
                    } catch (e: Exception) {
                        component.packageName
                    }

                    val icon = try {
                        appInfo.loadIcon(packageManager)
                    } catch (e: Exception) {
                        null
                    }

                    DeviceAdminApp(
                        info = adminInfo,
                        label = label,
                        packageName = component.packageName,
                        component = component,
                        icon = icon
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse DeviceAdminInfo: ${e.message}")
                    null
                }
            }.sortedBy { it.label.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query device admin apps: ${e.message}")
            emptyList()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun transferDeviceOwner(
        devicePolicyManager: DevicePolicyManager,
        currentPackageName: String,
        targetApp: DeviceAdminApp
    ) {
        try {
            val currentAdmin = ComponentName(currentPackageName, DeviceOwnerReceiver::class.java.name)
            val targetAdmin = targetApp.component
            val bundle = PersistableBundle()
            
            Log.i(TAG, "Transferring ownership from $currentAdmin to $targetAdmin")
            devicePolicyManager.transferOwnership(currentAdmin, targetAdmin, bundle)
            
            requireContext().toast(R.string.device_owner_transfer_success)
            
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                activity?.recreate()
            }, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to transfer device owner: ${e.message}", e)
            requireContext().toast(R.string.device_owner_transfer_failed)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Toolbar>(R.id.toolbar)?.apply {
            title = getString(R.string.title_installation)
            setNavigationOnClickListener { findNavController().navigateUp() }
        }
    }

    private class AppListAdapter(
        context: Context,
        private val apps: List<DeviceAdminApp>
    ) : ArrayAdapter<DeviceAdminApp>(context, 0, apps) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(
                android.R.layout.select_dialog_item,
                parent,
                false
            )

            val app = apps[position]
            
            view.findViewById<ImageView>(android.R.id.icon)?.apply {
                if (app.icon != null) {
                    setImageDrawable(app.icon)
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }

            view.findViewById<TextView>(android.R.id.text1)?.text = "${app.label}\n${app.packageName}"

            return view
        }
    }
}
