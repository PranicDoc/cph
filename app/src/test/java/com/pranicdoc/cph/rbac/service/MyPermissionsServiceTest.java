package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.rbac.dto.MyPermissionsResponse;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyPermissionsServiceTest {

    @Mock private RbacAuthz rbacAuthz;
    @Mock private SecurityContextUtil securityContextUtil;
    @Mock private RolePermissionLookupService lookupService;
    @InjectMocks private MyPermissionsService service;

    @Test
    void superAdminIsFlaggedWithoutReadingGrants() {
        when(rbacAuthz.isSuperAdmin()).thenReturn(true);

        MyPermissionsResponse me = service.current();

        assertThat(me.superAdmin()).isTrue();
        assertThat(me.grants()).isEmpty();
        verifyNoInteractions(lookupService);
    }

    @Test
    void otherRolesGetTheirEffectiveGrantsAsNames() {
        UUID healerId = UUID.randomUUID();
        when(rbacAuthz.isSuperAdmin()).thenReturn(false);
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(lookupService.effectiveGrants(healerId)).thenReturn(Map.of(
                Resource.MASTER_DATA_PROTOCOLS, EnumSet.of(Operation.READ, Operation.CREATE)));

        MyPermissionsResponse me = service.current();

        assertThat(me.superAdmin()).isFalse();
        assertThat(me.grants()).containsOnlyKeys("MASTER_DATA_PROTOCOLS");
        assertThat(me.grants().get("MASTER_DATA_PROTOCOLS")).containsExactly("READ", "CREATE");
    }
}
