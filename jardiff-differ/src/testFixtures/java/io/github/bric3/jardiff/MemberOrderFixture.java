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

public class MemberOrderFixture {
    public int zebra = 1;
    private String alpha = "alpha";
    protected static final long middle = 42L;

    public MemberOrderFixture() {
    }

    public String zeta(String value) {
        return value + zebra;
    }

    protected static String middle() {
        return "middle";
    }

    private int alpha(int value) {
        return value + 1;
    }

    public int overloaded(int value) {
        return value;
    }

    public int overloaded(String value) {
        return value.length();
    }
}
