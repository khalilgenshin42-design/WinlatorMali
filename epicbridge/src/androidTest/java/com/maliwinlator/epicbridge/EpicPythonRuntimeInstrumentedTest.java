package com.maliwinlator.epicbridge;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class EpicPythonRuntimeInstrumentedTest {
    @Test
    public void embeddedLegendaryRuntimeAndAndroidCryptoWork() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("CPython assets and JNI library must be packaged",
                EpicPythonRuntime.isPackaged(context));

        String output = EpicPythonRuntime.call(context, "runtime_self_test", "{}");
        JSONObject result = new JSONObject(output);
        assertTrue("Runtime self-test failed: " + output, result.optBoolean("ok", false));

        JSONObject checks = result.getJSONObject("self_test");
        assertTrue(checks.optBoolean("android_jca_aes_gcm", false));
        assertTrue(checks.optBoolean("thread_workers", false));
        assertTrue(checks.optBoolean("anonymous_mmap", false));
        assertTrue("Legendary 0.21.1 must be installed",
                "0.21.1".equals(checks.optString("legendary")));
    }
}
