/*
 * Copyright 2020 Gabriel Keller
 * This work is licensed under a Creative Commons
 * Attribution-NonCommercial-ShareAlike 4.0 International License.
 * A copy of this license can be found at
 * https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode.
 */

package com.okgabe.mastr2.util;

public class Checks {
    public static boolean isEmptyString(String s){
        return s == null || s.trim().isEmpty() || s.equalsIgnoreCase("null");
    }

    public static boolean isId(String s){
        if(isEmptyString(s)) return false;
        return s.length() == 18 && StringUtil.isNumeric(s);
    }
}
