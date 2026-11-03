package com.pranicdoc.cph.patient.model;

import java.security.SecureRandom;

/** Makes patient codes like PT-K7M2Q9: easy to read out, no look-alike characters. */
public final class PatientCodes {
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PatientCodes() {
    }

    /** A random code with at least one letter, so it can never clash with the numeric codes given to existing patients. */
    public static String next() {
        while (true) {
            StringBuilder code = new StringBuilder("PT-");
            boolean hasLetter = false;
            for (int i = 0; i < 6; i++) {
                char c = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
                hasLetter |= Character.isLetter(c);
                code.append(c);
            }
            if (hasLetter) {
                return code.toString();
            }
        }
    }
}
