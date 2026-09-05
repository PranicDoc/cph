package com.pranicdoc.cph.rbac.baseline;

import com.pranicdoc.cph.rbac.baseline.BaselineRoleGrantReconciler.ReconcileResult;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.RbacRole;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.repository.RbacRoleRepository;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaselineRoleGrantReconcilerTest {

    @Mock private RbacRoleRepository rbacRoleRepository;
    @Mock private BaselineRoleGrantStore grantStore;

    @InjectMocks private BaselineRoleGrantReconciler reconciler;

    private static final UUID ROLE_ID = UUID.randomUUID();

    private static RbacRole practitioner() {
        RbacRole r = RbacRole.builder().name("Practitioner").build();
        r.setId(ROLE_ID);
        return r;
    }

    /** Every (resource, verb) in the matrix, as the [resource, operation, is_deleted] native rows would look. */
    private static List<Object[]> rowsForFullMatrix(boolean deleted) {
        List<Object[]> rows = new ArrayList<>();
        BaselineRole.GRANTS.forEach((resource, ops) ->
                ops.forEach(op -> rows.add(new Object[] {resource.name(), op.name(), deleted})));
        return rows;
    }

    @Test
    void reconcile_whenRoleAbsent_createsItAndInsertsEveryGrant() {
        when(rbacRoleRepository.findByName("Practitioner")).thenReturn(Optional.empty());
        when(rbacRoleRepository.saveAndFlush(any(RbacRole.class))).thenAnswer(inv -> {
            RbacRole r = inv.getArgument(0);
            r.setId(ROLE_ID);
            return r;
        });
        when(grantStore.findAllRowsIncludingSoftDeleted(ROLE_ID)).thenReturn(List.of());

        int expectedGrants = BaselineRole.GRANTS.values().stream().mapToInt(java.util.Set::size).sum();

        ReconcileResult result = reconciler.reconcile(false);

        assertThat(result.roleCreated()).isTrue();
        assertThat(result.roleId()).isEqualTo(ROLE_ID);
        assertThat(result.added()).isEqualTo(expectedGrants);
        assertThat(result.reactivated()).isZero();
        assertThat(result.removed()).isZero();
        verify(grantStore, never()).setGrantDeleted(any(), any(), any(), any(Boolean.class));
    }

    @Test
    void reconcile_whenGrantsAlreadyMatch_writesNothing() {
        when(rbacRoleRepository.findByName("Practitioner")).thenReturn(Optional.of(practitioner()));
        when(grantStore.findAllRowsIncludingSoftDeleted(ROLE_ID))
                .thenReturn(rowsForFullMatrix(false));

        ReconcileResult result = reconciler.reconcile(false);

        assertThat(result.roleCreated()).isFalse();
        assertThat(result.added()).isZero();
        assertThat(result.reactivated()).isZero();
        assertThat(result.removed()).isZero();
        verify(grantStore, never()).insertGrant(any(), any(), any());
        verify(grantStore, never()).setGrantDeleted(any(), any(), any(), any(Boolean.class));
    }

    @Test
    void reconcile_reactivatesSoftDeletedGrantsThatAreBackInTheMatrix() {
        when(rbacRoleRepository.findByName("Practitioner")).thenReturn(Optional.of(practitioner()));
        when(grantStore.findAllRowsIncludingSoftDeleted(ROLE_ID))
                .thenReturn(rowsForFullMatrix(true)); // whole matrix present but soft-deleted

        ReconcileResult result = reconciler.reconcile(false);

        int expectedGrants = BaselineRole.GRANTS.values().stream().mapToInt(java.util.Set::size).sum();
        assertThat(result.added()).isZero();
        assertThat(result.reactivated()).isEqualTo(expectedGrants);
        assertThat(result.removed()).isZero();
        verify(grantStore, never()).insertGrant(any(), any(), any());
        verify(grantStore)
                .setGrantDeleted(ROLE_ID, Resource.PATIENT.name(), Operation.READ.name(), false);
    }

    @Test
    void reconcile_softDeletesLiveGrantsNoLongerInTheMatrix() {
        when(rbacRoleRepository.findByName("Practitioner")).thenReturn(Optional.of(practitioner()));
        List<Object[]> rows = rowsForFullMatrix(false);
        rows.add(new Object[] {Resource.CASE.name(), Operation.DELETE.name(), false}); // stray live grant
        when(grantStore.findAllRowsIncludingSoftDeleted(ROLE_ID)).thenReturn(rows);

        ReconcileResult result = reconciler.reconcile(false);

        assertThat(result.added()).isZero();
        assertThat(result.reactivated()).isZero();
        assertThat(result.removed()).isEqualTo(1);
        verify(grantStore)
                .setGrantDeleted(ROLE_ID, Resource.CASE.name(), Operation.DELETE.name(), true);
    }

    @Test
    void reconcile_dryRun_computesCountsButWritesNothing() {
        when(rbacRoleRepository.findByName("Practitioner")).thenReturn(Optional.of(practitioner()));
        List<Object[]> rows = rowsForFullMatrix(false);
        rows.add(new Object[] {Resource.CASE.name(), Operation.DELETE.name(), false});
        when(grantStore.findAllRowsIncludingSoftDeleted(ROLE_ID)).thenReturn(rows);

        ReconcileResult result = reconciler.reconcile(true);

        assertThat(result.removed()).isEqualTo(1);
        verify(rbacRoleRepository, never()).saveAndFlush(any());
        verify(grantStore, never()).insertGrant(any(), any(), any());
        verify(grantStore, never()).setGrantDeleted(any(), any(), any(), any(Boolean.class));
    }

    @Test
    void reconcile_dryRun_whenRoleAbsent_reportsWouldCreateWithoutSaving() {
        when(rbacRoleRepository.findByName("Practitioner")).thenReturn(Optional.empty());

        ReconcileResult result = reconciler.reconcile(true);

        int expectedGrants = BaselineRole.GRANTS.values().stream().mapToInt(java.util.Set::size).sum();
        assertThat(result.roleCreated()).isTrue();
        assertThat(result.roleId()).isNull();
        assertThat(result.added()).isEqualTo(expectedGrants);
        verify(rbacRoleRepository, never()).saveAndFlush(any());
        verifyNoInteractions(grantStore);
    }
}
