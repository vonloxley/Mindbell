package com.googlecode.mindbell.test.accessors;

import android.test.AndroidTestCase;

import com.googlecode.mindbell.accessors.AndroidContextAccessor;
import com.googlecode.mindbell.accessors.ContextAccessor;

public class AndroidContextAccessorTest extends AndroidTestCase {

    private ContextAccessor createContextAccessor() {
        return AndroidContextAccessor.get(getContext());
    }

    public void testBellVolume() {
        // setup
        ContextAccessor ca = createContextAccessor();
        // exercise
        ca.startBellSound(null);
        // verify
        assertEquals(ca.getBellVolume(), ca.getMusicVolume());
    }

    public void testFinish() {
        // setup
        ContextAccessor ca = createContextAccessor();
        ca.startBellSound(null);
        // exercise
        ca.finishBellSound();
        // verify
        assertFalse(ca.isBellSoundPlaying());

    }

    public void testOriginalVolume() {
        // setup
        ContextAccessor ca = createContextAccessor();
        int originalVolume = ca.getMusicVolume();
        // exercise
        ca.startBellSound(null);
        ca.finishBellSound();
        // verify
        assertEquals(originalVolume, ca.getMusicVolume());

    }

    public void testPlay() {
        // setup
        ContextAccessor ca = createContextAccessor();
        // exercise
        ca.startBellSound(null);
        // verify
        assertTrue(ca.isBellSoundPlaying());
    }
}
