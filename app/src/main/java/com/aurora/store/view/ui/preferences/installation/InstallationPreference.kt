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

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
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

        // Show or hide the entire Device Owner category
        findPreference<PreferenceCategory>(PREFERENCE_CATEGORY_DEVICE_OWNER)?.isVisible = isDeviceOwner

        // Clear Device Owner preference
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

        // Transfer Device Owner preference
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
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.target_package_name)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.transfer_device_owner)
            .setMessage(R.string.transfer_device_owner_desc)
            .setView(input)
            .setPositiveButton(R.string.transfer) { _, _ ->
                val targetPackageName = input.text.toString().trim()
                if (targetPackageName.isNotEmpty()) {
                    transferDeviceOwner(devicePolicyManager, currentPackageName, targetPackageName)
                } else {
                    requireContext().toast("Please enter a valid package name")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun transferDeviceOwner(
        devicePolicyManager: DevicePolicyManager,
        currentPackageName: String,
        targetPackageName: String
    ) {
        try {
            val currentAdmin = ComponentName(currentPackageName, DeviceOwnerReceiver::class.java.name)
            
            // Try to find the target app's DeviceAdminReceiver
            val targetPm = requireContext().packageManager
            val targetIntent = android.content.Intent(android.app.admin.DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED)
            targetIntent.setPackage(targetPackageName)
            
            val receivers = targetPm.queryBroadcastReceivers(targetIntent, 0)
            if (receivers.isEmpty()) {
                requireContext().toast(R.string.device_owner_transfer_failed)
                return
            }

            val targetAdmin = ComponentName(targetPackageName, receivers[0].activityInfo.name)
            val bundle = PersistableBundle()
            
            devicePolicyManager.transferOwnership(currentAdmin, targetAdmin, bundle)
            requireContext().toast(R.string.device_owner_transfer_success)
            
            // Give some time for the transfer to complete before recreating
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
}
