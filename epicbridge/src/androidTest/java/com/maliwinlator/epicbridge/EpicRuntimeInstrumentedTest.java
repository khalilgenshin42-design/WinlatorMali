package com.maliwinlator.epicbridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class EpicRuntimeInstrumentedTest {
    @Test
    public void providerAuthorizesOnlyTheActualStandardHostPackage() {
        assertTrue(EpicBridgeProvider.isAllowedHostPackage("com.maliwinlator.app"));
        assertFalse(EpicBridgeProvider.isAllowedHostPackage("com.winlator.cmod"));
        assertFalse(EpicBridgeProvider.isAllowedHostPackage("com.example.untrusted"));
    }

    @Test
    public void onlyOneEpicBackgroundJobCanBeReservedAtATime() {
        String first = java.util.UUID.randomUUID().toString();
        String second = java.util.UUID.randomUUID().toString();
        assertTrue(EpicRuntimeService.reserveJob(first));
        try {
            assertFalse(EpicRuntimeService.reserveJob(second));
            assertTrue(EpicRuntimeService.isJobActive(first));
            assertFalse(EpicRuntimeService.isJobActive(second));
        } finally {
            EpicRuntimeService.releaseJobReservation(first);
        }
        assertTrue(EpicRuntimeService.reserveJob(second));
        EpicRuntimeService.releaseJobReservation(second);
    }

    @Test
    public void bundledRuntimeLoadsAndPassesNativeSelfTest() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertTrue("CPython/Legendary assets or JNI library are missing",
                EpicPythonRuntime.isPackaged(context));
        assertTrue("Android AES-GCM known-answer or invalid-tag test failed",
                EpicCryptoBridge.verifyKnownAnswerTest());

        String response = EpicPythonRuntime.call(context, "runtime_self_test", "{}");
        JSONObject result = new JSONObject(response);
        assertTrue("Runtime self-test failed: " + result.optString("message", response),
                result.optBoolean("ok", false));

        JSONObject selfTest = result.getJSONObject("self_test");
        assertEquals("0.21.1", selfTest.getString("legendary"));
        assertTrue("Android JCA AES-GCM was not exercised",
                selfTest.getBoolean("android_jca_aes_gcm"));
        assertTrue("JCA callback from a Python worker thread failed",
                selfTest.getBoolean("android_jca_aes_gcm_worker_thread"));
        assertTrue("JNI AES-GCM accepted a corrupted authentication tag",
                selfTest.getBoolean("android_jca_aes_gcm_bad_tag_rejected"));
        assertTrue("Legendary workers are not using the thread backend",
                selfTest.getBoolean("thread_workers"));
        assertTrue("Download-manager exception cleanup test failed",
                selfTest.getBoolean("download_failure_cleanup"));
        assertTrue("Anonymous mmap test failed",
                selfTest.getBoolean("anonymous_mmap"));
    }
}
