package com.pockyl.inventorybackups.gametest;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.pockyl.inventorybackups.InventoryBackups;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Registers the {@link ModTest} methods of the test classes as game tests (registry based since Minecraft 1.21.5).
 * A test is named after its method in snake case, for example {@code inventorybackups:mod_loads}.
 */
public final class GameTests {
    private static final Identifier EMPTY = InventoryBackups.id("empty");
    private static final List<Class<?>> CLASSES = List.of(ModGameTests.class, CaptureTests.class, FlowTests.class, IndexTests.class,
            MigrationTests.class, ModSlotTests.class, NetworkTests.class, OfflineTests.class, PerformanceTests.class,
            PermissionTests.class, RetentionTests.class, SearchTests.class, StorageTests.class);
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION,
            InventoryBackups.MOD_ID);
    private static final List<Test> TESTS = new ArrayList<>();

    private record Test(String name, Method method, int timeoutTicks) {
    }

    private GameTests() {
    }

    /** A failure thrown outside a helper (static test utilities). */
    static GameTestAssertException failure(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }

    public static void register(IEventBus modBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        for (Class<?> type : CLASSES) {
            List<Method> methods = new ArrayList<>(List.of(type.getDeclaredMethods()));
            methods.sort(Comparator.comparing(Method::getName));
            for (Method method : methods) {
                ModTest test = method.getAnnotation(ModTest.class);
                if (test == null) {
                    continue;
                }
                if (!Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalStateException("Game test " + method + " is not static");
                }
                method.setAccessible(true);
                Test entry = new Test(snakeCase(method.getName()), method, test.timeoutTicks());
                TESTS.add(entry);
                FUNCTIONS.register(entry.name(), () -> helper -> run(entry.method(), helper));
            }
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(GameTests::registerTests);
    }

    private static void run(Method method, GameTestHelper helper) {
        try {
            method.invoke(null, helper);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(InventoryBackups.id("default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        for (Test test : TESTS) {
            Identifier id = InventoryBackups.id(test.name());
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, Level.OVERWORLD, EMPTY, test.timeoutTicks(), 0, true,
                    Rotation.NONE, false, 1, 1, false, 0);
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id), data));
        }
    }

    private static String snakeCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
