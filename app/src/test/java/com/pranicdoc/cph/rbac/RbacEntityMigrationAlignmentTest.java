package com.pranicdoc.cph.rbac;

import com.pranicdoc.cph.common.model.BaseEntity;
import com.pranicdoc.cph.rbac.model.RbacRole;
import com.pranicdoc.cph.rbac.model.RolePermission;
import com.pranicdoc.cph.rbac.model.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;
import org.hibernate.envers.Audited;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain-JUnit (no Spring context) two-way drift gate between the three
 * {@code com.pranicdoc.cph.rbac.model} entities and
 * {@code db/migration/V33__create_rbac_schema.sql}. Resource and Operation
 * are static Java enums, not DB-seeded tables, so they carry no
 * migration-alignment gate of their own — RolePermission's resource/operation
 * columns are plain VARCHAR (enum name), covered by the same field-to-column
 * reflection this class already does.
 * <p>
 * This gate exists because {@code application-test.yaml} disables Flyway for
 * the test profile (H2 cannot parse {@code TIMESTAMPTZ}) and instead
 * generates the test schema straight from JPA annotations via
 * {@code ddl-auto: create-drop}. That combination means Hibernate always
 * "sees" a schema shaped exactly like the entities — it can never notice
 * that an entity column and V30's real Postgres DDL have drifted apart. And
 * because full Spring application-context boot is itself currently broken in
 * this repo ({@code TenantFilterIntegrationTest} fails at
 * {@code PropertyPlaceholderHelper}), even a schema-validation integration
 * test isn't available as a fallback. This class closes that hole entirely
 * offline: it reads V30's raw SQL off the classpath, reflects over the six
 * entity classes, and compares the two column-name sets directly, in both
 * directions.
 */
class RbacEntityMigrationAlignmentTest {

    private static final String RESOURCE_PATH = "db/migration/V33__create_rbac_schema.sql";

    private static final Map<Class<?>, String> ENTITY_TABLES = new LinkedHashMap<>();

    static {
        ENTITY_TABLES.put(RbacRole.class, "rbac_role");
        ENTITY_TABLES.put(RolePermission.class, "role_permission");
        ENTITY_TABLES.put(UserRole.class, "user_role");
    }

    /** V30's SQL with every {@code --} line comment stripped — every assertion in this class runs against this view, never the raw file. */
    private static String SQL;

    @BeforeAll
    static void loadMigration() throws IOException {
        String raw;
        try (InputStream in = RbacEntityMigrationAlignmentTest.class.getClassLoader()
                .getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(in, "Migration resource not found on classpath: " + RESOURCE_PATH);
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        SQL = stripComments(raw);
    }

    // -------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------

    @Test
    void everyEntityColumnExistsInTheMigration() {
        for (Map.Entry<Class<?>, String> entry : ENTITY_TABLES.entrySet()) {
            Class<?> entityClass = entry.getKey();
            String table = entry.getValue();
            Set<String> ddlColumns = extractColumnNames(SQL, table);
            Map<String, String> entityColumns = reflectColumnNames(entityClass);

            for (Map.Entry<String, String> col : entityColumns.entrySet()) {
                assertTrue(ddlColumns.contains(col.getValue()),
                        () -> entityClass.getSimpleName() + "." + col.getKey() + " maps to column '"
                                + col.getValue() + "' which does not exist in table '" + table + "'");
            }
        }
    }

    @Test
    void everyMigrationColumnExistsOnTheEntity() {
        for (Map.Entry<Class<?>, String> entry : ENTITY_TABLES.entrySet()) {
            Class<?> entityClass = entry.getKey();
            String table = entry.getValue();
            Set<String> ddlColumns = extractColumnNames(SQL, table);
            Set<String> entityColumnValues = new HashSet<>(reflectColumnNames(entityClass).values());

            for (String ddlColumn : ddlColumns) {
                assertTrue(entityColumnValues.contains(ddlColumn),
                        () -> "Migration column '" + ddlColumn + "' in table '" + table
                                + "' has no matching field on " + entityClass.getSimpleName());
            }
        }
    }

    @Test
    void everyAuditTableMirrorsItsBaseTable() {
        for (String table : ENTITY_TABLES.values()) {
            Set<String> baseColumns = extractColumnNames(SQL, table);
            Set<String> audColumns = extractColumnNames(SQL, table + "_aud");

            Set<String> expectedAudColumns = new LinkedHashSet<>(baseColumns);
            expectedAudColumns.add("rev");
            expectedAudColumns.add("revtype");

            assertEquals(expectedAudColumns, audColumns,
                    table + "_aud must equal " + table + "'s columns plus exactly {rev, revtype}");
        }
    }

    @Test
    void everyEntityIsAuditedAndExtendsBaseEntity() {
        for (Class<?> entityClass : ENTITY_TABLES.keySet()) {
            assertEquals(BaseEntity.class, entityClass.getSuperclass(),
                    entityClass.getSimpleName() + " must extend BaseEntity — this is what makes its _aud table load-bearing rather than decorative");
            assertTrue(entityClass.isAnnotationPresent(Audited.class),
                    entityClass.getSimpleName() + " must carry @Audited");
        }
    }

    @Test
    void noEntityDeclaresAJpaAssociation() {
        List<Class<? extends Annotation>> forbidden = List.of(
                ManyToOne.class, OneToMany.class, ManyToMany.class, JoinColumn.class);

        for (Class<?> entityClass : ENTITY_TABLES.keySet()) {
            for (Field field : entityClass.getDeclaredFields()) {
                for (Class<? extends Annotation> annotation : forbidden) {
                    assertFalse(field.isAnnotationPresent(annotation),
                            entityClass.getSimpleName() + "." + field.getName()
                                    + " must not declare " + annotation.getSimpleName()
                                    + " — the rbac module deliberately uses plain UUID columns (the ProtocolColorMapping house pattern)");
                }
            }
        }
    }

    // -------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------

    /**
     * Builds a {@code fieldName -> columnName} map for {@code entityClass}'s
     * own declared fields plus {@link BaseEntity}'s declared fields, skipping
     * {@code static}, synthetic and {@code @Transient} fields. The column
     * name is the field's {@code @Column(name = ...)} value when present,
     * otherwise the field name converted camelCase-to-snake_case — this is
     * the path that must resolve {@code BaseEntity.version} (no
     * {@code @Column}, only {@code @Version}) correctly.
     */
    private static Map<String, String> reflectColumnNames(Class<?> entityClass) {
        Map<String, String> columns = new LinkedHashMap<>();
        List<Field> fields = new ArrayList<>();
        fields.addAll(List.of(BaseEntity.class.getDeclaredFields()));
        fields.addAll(List.of(entityClass.getDeclaredFields()));

        for (Field field : fields) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.isAnnotationPresent(Transient.class)) {
                continue;
            }
            Column column = field.getAnnotation(Column.class);
            String columnName = (column != null && !column.name().isEmpty())
                    ? column.name()
                    : camelToSnake(field.getName());
            columns.put(field.getName(), columnName);
        }
        return columns;
    }

    private static String camelToSnake(String input) {
        StringBuilder out = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isUpperCase(c)) {
                out.append('_').append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    // -------------------------------------------------------------------
    // DDL parsing helpers — deliberately hand-rolled and local to this
    // class, no SQL-parser dependency added. Reuses the same
    // CREATE-TABLE-block-extraction shape as V30RbacSchemaMigrationTest.
    // -------------------------------------------------------------------

    private static String stripComments(String raw) {
        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\n", -1)) {
            int idx = line.indexOf("--");
            out.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return out.toString();
    }

    /**
     * Extracts the column/constraint body of {@code CREATE TABLE tableName (
     * ... );}, excluding the {@code CREATE TABLE tableName (} prefix line
     * itself, so that a line-based first-token scan never mistakes the
     * "CREATE" keyword for a column name. The trailing " (" in the prefix
     * guards against {@code resource} matching inside
     * {@code resource_permission}.
     */
    private static String extractCreateTableBody(String sql, String tableName) {
        String prefix = "CREATE TABLE " + tableName + " (";
        int start = sql.indexOf(prefix);
        assertTrue(start >= 0, "Expected to find: " + prefix);
        start += prefix.length();
        int end = sql.indexOf(");", start);
        assertTrue(end >= 0, "Expected terminator ');' after " + prefix);
        return sql.substring(start, end);
    }

    /**
     * Parses a {@code CREATE TABLE} body into its column-name set: the first
     * whitespace-delimited token of every non-blank line, discarding lines
     * whose first token is {@code CONSTRAINT}.
     */
    private static Set<String> extractColumnNames(String sql, String tableName) {
        String body = extractCreateTableBody(sql, tableName);
        Set<String> columns = new LinkedHashSet<>();
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String firstToken = trimmed.split("\\s+")[0];
            if ("CONSTRAINT".equals(firstToken)) {
                continue;
            }
            columns.add(firstToken);
        }
        return columns;
    }
}
