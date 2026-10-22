package com.pranicdoc.cph.rbac.dto;

import java.util.List;
import java.util.Map;

/**
 * What the signed-in user may do, for the UI to decide which admin screens to show.
 * The server still checks every request; this only hides what would be refused.
 *
 * @param superAdmin SUPADM: every permission, so {@code grants} is left empty
 * @param grants     resource name to the verbs granted on it, across every role the user holds
 */
public record MyPermissionsResponse(boolean superAdmin, Map<String, List<String>> grants) {
}
