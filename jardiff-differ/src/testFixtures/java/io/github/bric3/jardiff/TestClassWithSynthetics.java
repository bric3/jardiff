/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.bric3.jardiff;

/**
 * Test class that naturally generates synthetic members when compiled.
 *
 * Inner classes generate:
 * - Synthetic field (this$0 reference to outer class)
 * - Synthetic access methods for accessing private outer class members
 *
 * Generic bridge methods generate:
 * - Synthetic bridge methods for type erasure
 */
public class TestClassWithSynthetics {
    private String outerField = "outer";

    /**
     * Inner class that will have a synthetic this$0 field
     * and synthetic access methods.
     */
    public class InnerClass {
        public void accessOuterField() {
            // This access generates synthetic accessor to the outer this
            System.out.println(outerField);
        }
    }

    /**
     * Generic class that creates bridge methods.
     */
    public static class GenericBridge<T extends Comparable<T>> implements Comparable<GenericBridge<T>> {
        private T value;

        public GenericBridge(T value) {
            this.value = value;
        }

        // This will generate a synthetic bridge method
        @Override
        public int compareTo(GenericBridge<T> other) {
            if (this.value == null && other.value == null) return 0;
            if (this.value == null) return -1;
            if (other.value == null) return 1;
            return this.value.compareTo(other.value);
        }
    }
}