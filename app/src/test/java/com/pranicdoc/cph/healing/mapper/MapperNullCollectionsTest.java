package com.pranicdoc.cph.healing.mapper;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guard for every MapStruct mapper: feeding it partial input must never throw a NullPointerException
 * and must never produce an object with a null collection.
 *
 * <p>Why: entities are built through Lombok builders. A null list passed into a builder skips
 * {@code @Builder.Default}, so an omitted request list used to come out as a null entity list and
 * crash later on {@code .add(...)} (new session on a case with a final feedback, 2026-11-11). The
 * build sets MapStruct's {@code nullValueIterableMappingStrategy=RETURN_DEFAULT}; this test fails if
 * that ever regresses or a new mapper reintroduces the problem.
 *
 * <p>Each public mapper method is called with two shapes of input:
 * <ul>
 *   <li><b>all null</b> — every record component / field null (lists omitted, as the UI does);</li>
 *   <li><b>one empty element</b> — every list holds one element that is itself all-null, recursively,
 *       so nested mappings (scan → readings, details → steps) are exercised too.</li>
 * </ul>
 * Add a new mapper to {@link #MAPPERS} when you create one.
 */
class MapperNullCollectionsTest {

    private static final List<Object> MAPPERS = List.of(
            new HealingSessionMapperImpl(),
            new HealingCaseMapperImpl(),
            new DeclarationMapperImpl(),
            new CphCaseMapperImpl()
    );

    private static final int MAX_DEPTH = 4;

    @TestFactory
    Stream<DynamicTest> everyMapperMethodHandlesNullInputs() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Object mapper : MAPPERS) {
            for (Method method : mapper.getClass().getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()
                        || method.getParameterCount() == 0) continue;
                String name = mapper.getClass().getSimpleName() + "." + method.getName()
                        + Arrays.toString(Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName).toArray());
                tests.add(DynamicTest.dynamicTest(name + " [all null]", () -> check(mapper, method, false)));
                tests.add(DynamicTest.dynamicTest(name + " [one empty element]", () -> check(mapper, method, true)));
            }
        }
        return tests.stream();
    }

    private static void check(Object mapper, Method method, boolean fillLists) throws Exception {
        Object[] args = new Object[method.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            args[i] = instantiate(method.getParameterTypes()[i], method.getGenericParameterTypes()[i], fillLists, 0);
        }
        Object result;
        try {
            result = method.invoke(mapper, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof NullPointerException npe) {
                AssertionError err = new AssertionError("Mapper threw NPE on partial input: " + npe.getMessage(), npe);
                throw err;
            }
            throw e;
        }
        // A void (@MappingTarget) method mutates its target argument; check that instead.
        Object toInspect = method.getReturnType() == void.class ? args[args.length - 1] : result;
        List<String> nulls = new ArrayList<>();
        findNullCollections(toInspect, toInspect == null ? "" : toInspect.getClass().getSimpleName(),
                Collections.newSetFromMap(new IdentityHashMap<>()), nulls, 0);
        if (!nulls.isEmpty()) {
            fail("Null collection(s) in mapper output (should be empty): " + nulls);
        }
    }

    // ---- input construction ------------------------------------------------------------------------

    private static Object instantiate(Class<?> type, Type generic, boolean fillLists, int depth) throws Exception {
        if (type.isPrimitive()) return primitiveDefault(type);
        if (List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type)) {
            Collection<Object> c = Set.class.isAssignableFrom(type) ? new LinkedHashSet<>() : new ArrayList<>();
            if (fillLists && depth < MAX_DEPTH) {
                Class<?> element = elementType(generic);
                if (element != null && isOwnType(element)) c.add(instantiate(element, element, true, depth + 1));
            }
            return c;
        }
        if (!isOwnType(type) || type.isEnum() || type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            return null;
        }
        if (type.isRecord()) {
            RecordComponent[] comps = type.getRecordComponents();
            Class<?>[] types = Arrays.stream(comps).map(RecordComponent::getType).toArray(Class<?>[]::new);
            Object[] values = new Object[comps.length];
            for (int i = 0; i < comps.length; i++) {
                Class<?> t = comps[i].getType();
                // "all null": lists are omitted (null), as when the UI leaves them out.
                if (t.isPrimitive()) values[i] = primitiveDefault(t);
                else if (fillLists && (List.class.isAssignableFrom(t) || Set.class.isAssignableFrom(t)))
                    values[i] = instantiate(t, comps[i].getGenericType(), true, depth + 1);
                else values[i] = null;
            }
            Constructor<?> ctor = type.getDeclaredConstructor(types);
            ctor.setAccessible(true);
            return ctor.newInstance(values);
        }
        Constructor<?> ctor;
        try {
            ctor = type.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            return null;
        }
        ctor.setAccessible(true);
        Object obj = ctor.newInstance();
        if (fillLists && depth < MAX_DEPTH) {
            for (Field f : allFields(type)) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (!List.class.isAssignableFrom(f.getType()) && !Set.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Class<?> element = elementType(f.getGenericType());
                if (element == null || !isOwnType(element)) continue;
                @SuppressWarnings("unchecked")
                Collection<Object> c = (Collection<Object>) f.get(obj);
                if (c == null) continue; // entity with a null default: reported by the output check if mapped
                Object child = instantiate(element, element, true, depth + 1);
                if (child != null) c.add(child);
            }
        }
        return obj;
    }

    // ---- output inspection -------------------------------------------------------------------------

    private static void findNullCollections(Object obj, String path, Set<Object> seen, List<String> out, int depth)
            throws IllegalAccessException {
        if (obj == null || depth > MAX_DEPTH + 2 || !isOwnType(obj.getClass()) || obj.getClass().isEnum()
                || obj instanceof Enum<?> || !seen.add(obj)) return;
        for (Field f : allFields(obj.getClass())) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            Object value = f.get(obj);
            String p = path + "." + f.getName();
            if (Collection.class.isAssignableFrom(f.getType()) || Map.class.isAssignableFrom(f.getType())) {
                if (value == null) {
                    out.add(p);
                } else if (value instanceof Collection<?> c) {
                    int i = 0;
                    for (Object item : c) findNullCollections(item, p + "[" + i++ + "]", seen, out, depth + 1);
                }
            } else {
                findNullCollections(value, p, seen, out, depth + 1);
            }
        }
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private static boolean isOwnType(Class<?> c) {
        return c.getName().startsWith("com.pranicdoc.");
    }

    private static Class<?> elementType(Type generic) {
        if (generic instanceof ParameterizedType pt && pt.getActualTypeArguments()[0] instanceof Class<?> c) return c;
        return null;
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields;
    }

    private static Object primitiveDefault(Class<?> t) {
        if (t == boolean.class) return false;
        if (t == char.class) return '\0';
        if (t == long.class) return 0L;
        if (t == float.class) return 0f;
        if (t == double.class) return 0d;
        if (t == byte.class) return (byte) 0;
        if (t == short.class) return (short) 0;
        return 0;
    }
}
