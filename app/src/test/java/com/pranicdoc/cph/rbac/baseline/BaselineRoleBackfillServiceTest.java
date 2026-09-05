package com.pranicdoc.cph.rbac.baseline;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.rbac.baseline.BaselineRoleGrantReconciler.ReconcileResult;
import com.pranicdoc.cph.rbac.dto.RoleAssignmentRequest;
import com.pranicdoc.cph.rbac.service.RoleAssignmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaselineRoleBackfillServiceTest {

    @Mock private BaselineRoleGrantReconciler grantReconciler;
    @Mock private HealerRepository healerRepository;
    @Mock private RoleAssignmentService roleAssignmentService;

    @InjectMocks private BaselineRoleBackfillService service;

    private static final UUID ROLE_ID = UUID.randomUUID();

    private static ReconcileResult reconciled(int added, int reactivated, int removed) {
        return new ReconcileResult(ROLE_ID, false, added, reactivated, removed);
    }

    @Test
    void run_assignsTheRoleToEveryHealerWithNoLiveRole_andPropagatesGrantCounts() {
        UUID h1 = UUID.randomUUID();
        UUID h2 = UUID.randomUUID();
        when(grantReconciler.reconcile(false)).thenReturn(reconciled(3, 1, 2));
        when(healerRepository.findActiveHealerIdsWithNoLiveRole()).thenReturn(List.of(h1, h2));

        BaselineRoleBackfillReport report = service.run(false);

        verify(roleAssignmentService).assign(new RoleAssignmentRequest(h1, ROLE_ID));
        verify(roleAssignmentService).assign(new RoleAssignmentRequest(h2, ROLE_ID));
        assertThat(report.roleId()).isEqualTo(ROLE_ID);
        assertThat(report.grantsAdded()).isEqualTo(3);
        assertThat(report.grantsReactivated()).isEqualTo(1);
        assertThat(report.grantsRemoved()).isEqualTo(2);
        assertThat(report.healersNeedingRole()).isEqualTo(2);
        assertThat(report.healersAssigned()).isEqualTo(2);
        assertThat(report.healersFailed()).isZero();
        assertThat(report.dryRun()).isFalse();
    }

    @Test
    void run_whenOneAssignmentThrows_countsItAsFailedAndKeepsGoing() {
        UUID h1 = UUID.randomUUID();
        UUID h2 = UUID.randomUUID();
        UUID h3 = UUID.randomUUID();
        when(grantReconciler.reconcile(false)).thenReturn(reconciled(0, 0, 0));
        when(healerRepository.findActiveHealerIdsWithNoLiveRole()).thenReturn(List.of(h1, h2, h3));
        when(roleAssignmentService.assign(any())).thenReturn(null);
        when(roleAssignmentService.assign(new RoleAssignmentRequest(h2, ROLE_ID)))
                .thenThrow(new IllegalArgumentException("This user is already assigned this role."));

        BaselineRoleBackfillReport report = service.run(false);

        verify(roleAssignmentService).assign(new RoleAssignmentRequest(h3, ROLE_ID));
        assertThat(report.healersAssigned()).isEqualTo(2);
        assertThat(report.healersFailed()).isEqualTo(1);
    }

    @Test
    void run_dryRun_reportsTheTargetCountButAssignsNothing() {
        when(grantReconciler.reconcile(true)).thenReturn(new ReconcileResult(null, true, 22, 0, 0));
        when(healerRepository.findActiveHealerIdsWithNoLiveRole())
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));

        BaselineRoleBackfillReport report = service.run(true);

        verifyNoInteractions(roleAssignmentService);
        assertThat(report.dryRun()).isTrue();
        assertThat(report.roleId()).isNull();
        assertThat(report.grantsAdded()).isEqualTo(22);
        assertThat(report.healersNeedingRole()).isEqualTo(2);
        assertThat(report.healersAssigned()).isZero();
    }

    @Test
    void run_whenNoHealersNeedTheRole_isANoOpAssignment() {
        when(grantReconciler.reconcile(false)).thenReturn(reconciled(0, 0, 0));
        when(healerRepository.findActiveHealerIdsWithNoLiveRole()).thenReturn(List.of());

        BaselineRoleBackfillReport report = service.run(false);

        verifyNoInteractions(roleAssignmentService);
        assertThat(report.healersNeedingRole()).isZero();
        assertThat(report.healersAssigned()).isZero();
    }
}
