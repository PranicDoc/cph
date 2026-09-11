package com.pranicdoc.cph.rbac.service;

import com.pranicdoc.cph.rbac.dto.RbacCatalogResourceResponse;
import com.pranicdoc.cph.rbac.dto.RbacCatalogResourceResponse.VerbDto;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for {@link RbacCatalogService}. Plain JUnit -- the service has
 * no collaborators (it reads the static {@link Resource} / {@link Operation}
 * enums), so there is no Spring context and no Mockito here.
 *
 * <p>The label-completeness tests iterate {@code Resource.values()} /
 * {@code Operation.values()} rather than a hardcoded list of names, so adding a
 * new enum constant without a display label fails the build -- the
 * anti-vacuity gate 38-CONTEXT D-03 relies on.
 */
class RbacCatalogServiceTest {

    private final RbacCatalogService service = new RbacCatalogService();

    @Test
    void everyResourceConstantHasANonBlankDisplayLabel() {
        for (Resource resource : Resource.values()) {
            assertThat(RbacCatalogService.resourceLabel(resource))
                    .as("Resource %s must have a non-blank display label", resource.name())
                    .isNotNull()
                    .isNotBlank();
        }
    }

    @Test
    void everyOperationConstantHasANonBlankVerbLabel() {
        for (Operation operation : Operation.values()) {
            assertThat(RbacCatalogService.verbLabel(operation))
                    .as("Operation %s must have a non-blank verb label", operation.name())
                    .isNotNull()
                    .isNotBlank();
        }
    }

    @Test
    void everyOperationConstantHasANonBlankVerbDescription() {
        for (Operation operation : Operation.values()) {
            assertThat(RbacCatalogService.verbDescription(operation))
                    .as("Operation %s must have a non-blank verb description", operation.name())
                    .isNotNull()
                    .isNotBlank();
        }
    }

    @Test
    void onlyRolesUpdateIsMarkedLocked() {
        for (RbacCatalogResourceResponse entry : service.catalog()) {
            for (VerbDto verb : entry.verbs()) {
                boolean expectLocked = entry.resource().equals("ROLES") && verb.value().equals("UPDATE");
                assertThat(verb.locked())
                        .as("%s:%s locked flag", entry.resource(), verb.value())
                        .isEqualTo(expectLocked);
            }
        }
    }

    @Test
    void catalogHasOneEntryPerResourceTodayAndNoEmptyVerbList() {
        List<RbacCatalogResourceResponse> catalog = service.catalog();

        assertThat(catalog).hasSize(19);
        assertThat(catalog).allSatisfy(entry ->
                assertThat(entry.verbs())
                        .as("resource %s must expose at least one grantable verb", entry.resource())
                        .isNotEmpty());
    }

    @Test
    void rolesEntryExposesItsFourAllowListedVerbsInEnumNameOrder() {
        RbacCatalogResourceResponse roles = service.catalog().stream()
                .filter(entry -> entry.resource().equals("ROLES"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ROLES is missing from the catalog"));

        assertThat(roles.verbs())
                .extracting(VerbDto::value)
                .containsExactly("CREATE", "DELETE", "READ", "UPDATE");
    }

    @Test
    void noLabelInThePayloadEqualsItsOwnRawEnumToken() {
        for (RbacCatalogResourceResponse entry : service.catalog()) {
            assertThat(entry.label())
                    .as("resource label must never be the raw token %s", entry.resource())
                    .isNotEqualTo(entry.resource());
            for (VerbDto verb : entry.verbs()) {
                assertThat(verb.label())
                        .as("verb label must never be the raw token %s", verb.value())
                        .isNotEqualTo(verb.value());
            }
        }
    }

    @Test
    void catalogIsSortedAscendingByLabel() {
        List<String> labels = service.catalog().stream()
                .map(RbacCatalogResourceResponse::label)
                .toList();

        assertThat(labels).isSorted();
    }
}
