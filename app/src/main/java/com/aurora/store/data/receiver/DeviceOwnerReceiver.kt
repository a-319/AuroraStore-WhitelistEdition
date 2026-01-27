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

package com.aurora.store.data.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.util.Log

/**
 * DeviceAdminReceiver for Aurora Store to support Device Owner mode.
 * This allows the app to receive device owner capabilities from other apps
 * or be set as device owner via ADB.
 */
class DeviceOwnerReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "DeviceOwnerReceiver"
        private const val TARGET_DPC = "com.afwsamples.testdpc"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Aurora Store enabled as Device Admin")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Aurora Store disabled as Device Admin")
    }

    override fun onTransferOwnershipComplete(context: Context, bundle: PersistableBundle?) {
        super.onTransferOwnershipComplete(context, bundle)
        Log.i(TAG, "Device Owner transfer completed successfully")

        // קריאה לפונקציה שמעניקה את ההרשאות ל-Test DPC
        grantInstallationDelegation(context)
    }

    private fun grantInstallationDelegation(context: Context) {
        // תיקון ה-Service Casting
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceOwnerReceiver::class.java)

        if (dpm != null) {
            try {
                // הגדרת ה-Scope של התקנת אפליקציות
                val scopes = listOf(DevicePolicyManager.DELEGATION_PACKAGE_INSTALLATION)

                // הענקת הסמכות ל-Test DPC
                dpm.setDelegatedScopes(adminComponent, TARGET_DPC, scopes)
                
                Log.i(TAG, "Successfully granted DELEGATION_PACKAGE_INSTALLATION to $TARGET_DPC")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set delegated scopes: ${e.message}")
            }
        } else {
            Log.e(TAG, "DevicePolicyManager is null")
        }
    }

    override fun onTransferAffiliatedProfileOwnershipComplete(context: Context, user: android.os.UserHandle) {
        super.onTransferAffiliatedProfileOwnershipComplete(context, user)
        Log.i(TAG, "Affiliated Profile Owner transfer completed for user: $user")
    }
}
