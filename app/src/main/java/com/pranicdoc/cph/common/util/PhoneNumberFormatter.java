package com.pranicdoc.cph.common.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.springframework.stereotype.Component;

@Component
public class PhoneNumberFormatter {
    private final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
    private static final String DEFAULT_REGION = "IN";

    /**
     * Parses and formats a raw string into E.164 format (+919876543210)
     */
    public String formatToE164(String rawNumber) {
        if (rawNumber == null || rawNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be empty");
        }

        try {
            Phonenumber.PhoneNumber numberProto = phoneUtil.parse(rawNumber, DEFAULT_REGION);
            if (!phoneUtil.isValidNumber(numberProto)) {
                throw new IllegalArgumentException("Invalid phone number format: " + rawNumber);
            }
            return phoneUtil.format(numberProto, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            throw new IllegalArgumentException("Failed to parse phone number: " + rawNumber, e);
        }
    }
}
