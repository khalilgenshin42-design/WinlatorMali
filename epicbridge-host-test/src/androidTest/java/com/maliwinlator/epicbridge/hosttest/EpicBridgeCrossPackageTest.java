package com.maliwinlator.epicbridge.hosttest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Runs under the host package UID, independently of the companion process. */
@RunWith(AndroidJUnit4.class)
public final class EpicBridgeCrossPackageTest {
    private static final Uri PROVIDER =
            Uri.parse("content://com.maliwinlator.epicbridge.provider");

    @Test
    public void signedHostUidCanCallExportedEpicProvider() {
        Context hostContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.maliwinlator.app", hostContext.getPackageName());

        Bundle status = hostContext.getContentResolver().call(
                PROVIDER, "status", null, null);
        assertNotNull("Epic provider returned no response", status);
        assertEquals(2, status.getInt("protocol_version", -1));
        assertTrue("Provider rejected the host UID",
                status.getBoolean("installed", false));
        assertTrue("Companion runtime or crypto self-test is not ready",
                status.getBoolean("runtime_ready", false));
    }
}
