package com.pranicdoc.cph.rbac;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.service.RolePermissionLookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito decision-function test for {@link RbacAuthz}. No Spring
 * context, no MockMvc — standalone MockMvc in this repo never evaluates
 * {@code @PreAuthorize}, so this is the real automated gate for RBAC-02 and
 * RBAC-04's logic.
 */
@ExtendWith(MockitoExtension.class)
class RbacAuthzTest {

    @Mock
    private SecurityContextUtil securityContextUtil;

    @Mock
    private RolePermissionLookupService lookupService;

    @InjectMocks
    private RbacAuthz rbacAuthz;

    @Test
    void supadmIsPermittedWithoutAnyLookup() {
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");

        assertTrue(rbacAuthz.hasPermission("ROLES", "DELETE"));

        verifyNoInteractions(lookupService);
        verify(securityContextUtil, never()).getCurrentHealerId();
    }

    @Test
    void supadmIsPermittedForEveryRolesVerb() {
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");

        for (Operation op : List.of(Operation.READ, Operation.CREATE, Operation.UPDATE, Operation.DELETE)) {
            assertTrue(rbacAuthz.hasPermission("ROLES", op.name()));
        }

        verifyNoInteractions(lookupService);
    }

    @Test
    void nonSupadmWithGrantIsPermitted() {
        UUID healerId = UUID.randomUUID();
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(lookupService.hasPermission(eq(healerId), eq(Resource.ROLES), eq(Operation.CREATE)))
                .thenReturn(true);

        assertTrue(rbacAuthz.hasPermission("ROLES", "CREATE"));

        verify(lookupService).hasPermission(healerId, Resource.ROLES, Operation.CREATE);
    }

    @Test
    void nonSupadmWithoutGrantIsDenied() {
        UUID healerId = UUID.randomUUID();
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(lookupService.hasPermission(eq(healerId), eq(Resource.ROLES), eq(Operation.CREATE)))
                .thenReturn(false);

        assertFalse(rbacAuthz.hasPermission("ROLES", "CREATE"));
    }

    @Test
    void unknownResourceLiteralFailsClosed() {
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");

        assertFalse(rbacAuthz.hasPermission("NOT_A_RESOURCE", "READ"));

        verifyNoInteractions(lookupService);
    }

    /**
     * PD-36-06 fail-closed gate, two shapes of incomplete SecurityContext:
     * (1) no role at all (getCurrentRole itself throws), and (2) a resolvable
     * non-SUPADM role but no TenantDetails (getCurrentHealerId throws). Both
     * must return false, never propagate.
     */
    @Test
    void missingSecurityContextFailsClosed() {
        when(securityContextUtil.getCurrentRole())
                .thenThrow(new IllegalStateException("No role found in SecurityContext"));

        assertFalse(rbacAuthz.hasPermission("ROLES", "READ"));

        // Re-stub with doReturn: the prior stub already throws on invocation,
        // so a plain when(...) here would itself trigger that throw.
        org.mockito.Mockito.doReturn("HEALER").when(securityContextUtil).getCurrentRole();
        when(securityContextUtil.getCurrentHealerId())
                .thenThrow(new IllegalStateException("No tenant context found in SecurityContext, is the user authenticated?"));

        assertFalse(rbacAuthz.hasPermission("ROLES", "READ"));
    }

    /**
     * PD-36-08 structural gate: the SUPADM bypass literal may exist in
     * exactly one file across the entire rbac main source tree. Walks the
     * source directory (resolved relative to the module working directory,
     * not an absolute path), strips javadoc/block comments and line
     * comments from each file, and asserts the literal only survives in
     * RbacAuthz.java.
     */
    @Test
    void supadmBypassExistsInExactlyOnePlace() {
        Path rbacSourceRoot = Paths.get("src", "main", "java", "com", "pranicdoc", "cph", "rbac");
        assertTrue(Files.isDirectory(rbacSourceRoot), "Expected rbac source directory at " + rbacSourceRoot.toAbsolutePath());

        List<Path> offenders;
        try (Stream<Path> files = Files.walk(rbacSourceRoot)) {
            offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(this::containsSupadmLiteralOutsideComments)
                    .filter(p -> !p.getFileName().toString().equals("RbacAuthz.java"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertTrue(offenders.isEmpty(),
                "SUPADM literal found outside RbacAuthz.java in: " + offenders);
    }

    private boolean containsSupadmLiteralOutsideComments(Path javaFile) {
        String source;
        try {
            source = Files.readString(javaFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String stripped = source
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//.*", "");
        return stripped.contains("\"SUPADM\"");
    }
}
