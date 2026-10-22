package com.pranicdoc.cph.rbac.dto;

import java.util.List;
import java.util.Map;

/**
 * What the signed-in user may do, for the UI to decide which screens and menu items to show.
 * The server still checks every request; this only hides what would be refused.
 *
 * @param grants resource name to the verbs granted on it, across every role the user holds
 */
public record MyPermissionsResponse(Map<String, List<String>> grants) {
}
