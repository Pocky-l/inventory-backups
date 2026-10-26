package com.pockyl.inventorybackups.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegisterEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The shared "Pocky Mods" creative tab. Every mod of the author ships this same class: the first one to load registers
 * the tab, the others find it already registered, and each adds its own items. No common library is needed.
 * Keep this file identical across mods (it lives in the workspace template).
 */
public final class PockyModsTab {
    public static final ResourceLocation ID = new ResourceLocation("pockymods", "pocky_mods");
    public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ID);

    private PockyModsTab() {
    }

    /**
     * @param icon  icon used if this mod is the one registering the tab
     * @param items adds this mod's items to the tab
     */
    public static void register(IEventBus modBus, Supplier<ItemStack> icon, Consumer<CreativeModeTab.Output> items) {
        modBus.addListener((RegisterEvent event) -> event.register(Registries.CREATIVE_MODE_TAB, helper -> {
            if (!registered(event)) {
                helper.register(ID, CreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.pockymods.pocky_mods"))
                        .icon(icon)
                        .build());
            }
        }));
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == KEY) {
                items.accept(event);
            }
        });
    }

    private static boolean registered(RegisterEvent event) {
        Registry<CreativeModeTab> vanilla = event.getVanillaRegistry();
        if (vanilla != null) {
            return vanilla.containsKey(ID);
        }
        IForgeRegistry<CreativeModeTab> forge = event.getForgeRegistry();
        return forge != null && forge.containsKey(ID);
    }
}
