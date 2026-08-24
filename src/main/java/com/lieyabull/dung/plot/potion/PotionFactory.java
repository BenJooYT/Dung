package com.lieyabull.dung.plot.potion;

import com.lieyabull.dung.listener.PotionListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates Dung transformation potion items.
 */
public final class PotionFactory {

    private PotionFactory() {}

    /** Create a throwable potion item for the given definition. */
    public static ItemStack createPotion(PotionDefinition def) {
        ItemStack stack = new ItemStack(Material.SPLASH_POTION, 1);
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        if (meta == null) return stack;

        // Set a base potion type (water is the most neutral)
        meta.setBasePotionType(PotionType.WATER);
        // Color the potion liquid to reflect the potion type (forest = green, stone = gray)
        meta.setColor(potionColor(def));

        // Set display name
        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
        meta.displayName(serializer.deserialize(def.displayName()));

        // Set lore
        List<Component> lore = new ArrayList<>();
        lore.add(serializer.deserialize("§7Transmutation Elixir"));
        lore.add(serializer.deserialize("§7Hits all " + def.targetMaterials().size() + " eligible block types."));
        lore.add(serializer.deserialize("§7Range: §f" + def.maxRange() + " blocks"));
        lore.add(serializer.deserialize("§7Max blocks: §f" + def.maxTransformedBlocks()));
        lore.add(serializer.deserialize(""));
        lore.add(serializer.deserialize("§7Use on your own plot to transform"));
        lore.add(serializer.deserialize("§7natural blocks into new varieties."));
        lore.add(serializer.deserialize(""));
        lore.add(serializer.deserialize("§7§oWon't affect player-placed blocks"));
        lore.add(serializer.deserialize("§7§owithout §f/convert§7§o."));
        meta.lore(lore);

        stack.setItemMeta(meta);

        // Tag the potion
        PotionListener.tagPotion(stack, def.id());

        return stack;
    }

    /** The potion liquid color for a definition — reflects the potion type. */
    private static org.bukkit.Color potionColor(PotionDefinition def) {
        return switch (def.id()) {
            case "forest" -> org.bukkit.Color.GREEN;
            case "stone" -> org.bukkit.Color.GRAY;
            default -> org.bukkit.Color.WHITE;
        };
    }

    /** Create a Forest Transmutation Elixir item. */
    public static ItemStack createForestPotion() {
        return createPotion(PotionDefinition.FOREST);
    }

    /** Create a Stone Transmutation Elixir item. */
    public static ItemStack createStonePotion() {
        return createPotion(PotionDefinition.STONE);
    }
}