package com.maliwinlator.epicbridge.hosttest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises the embedded provider and JNI runtime under the real host package UID. */
@RunWith(AndroidJUnit4.class)
public final class EpicBridgeCrossPackageTest {
    @Test
    public void embeddedProviderStartsInRuntimeProcessAndInitializesPython() throws Exception {
        Context hostContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.maliwinlator.app", hostContext.getPackageName());
        Uri provider = Uri.parse("content://" + hostContext.getPackageName() + ".epicprovider");

        Bundle status = hostContext.getContentResolver().call(provider, "status", null, null);
        assertNotNull("Embedded Epic provider returned no response", status);
        assertEquals(2, status.getInt("protocol_version", -1));
        assertTrue("Embedded provider did not report a packaged runtime",
                status.getBoolean("runtime_ready", false));

        Bundle account = hostContext.getContentResolver().call(
                provider, "account_status", null, new Bundle());
        assertNotNull("Embedded Python runtime returned no account response", account);
        assertTrue("Provider rejected account_status", account.getBoolean("ok", false));
        String response = account.getString("result_json");
        assertNotNull("Python returned no account status JSON", response);
        new JSONObject(response);

        ActivityManager manager = (ActivityManager) hostContext.getSystemService(Context.ACTIVITY_SERVICE);
        boolean runtimeProcess = false;
        for (ActivityManager.RunningAppProcessInfo process : manager.getRunningAppProcesses()) {
            if ((hostContext.getPackageName() + ":epic_runtime").equals(process.processName)) {
                runtimeProcess = true;
                break;
            }
        }
        assertTrue("Provider did not start in the dedicated :epic_runtime process", runtimeProcess);
    }
}
