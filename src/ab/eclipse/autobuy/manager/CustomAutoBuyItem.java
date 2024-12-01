package ab.eclipse.autobuy.manager;

import com.google.common.collect.Multimap;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.INBT;
import net.minecraft.util.text.ITextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static ab.eclipse.EclipseFuntime.mc;

public class CustomAutoBuyItem extends AutoBuyItem {
    public String name;
    public List<String> enchantments = new ArrayList<>();
    public List<String> line = new ArrayList<>();
    public List<String> attributes = new ArrayList<>();
    public List<String> effect = new ArrayList<>();
    public boolean strictCheck = false;

    public CustomAutoBuyItem(Item item, int price) {
        this.item = item;
        this.price = price;
    }

    public boolean tryBuy(ItemStack stack, int price) {
        if (!stack.getItem().equals(item)) return false;
        if (price / stack.getCount() > this.price) return false;

        // Проверка эффектов
        if (!effect.isEmpty()) {
            if (stack.getTag() == null || !stack.getTag().contains("sphereEffect")) return false;

            String sphereEffect = stack.getTag().get("sphereEffect").getString();
            if (sphereEffect == null || sphereEffect.isEmpty()) return false;

            List<String> eff = new ArrayList<>();
            String lvl = "";
            int c = 0;

            for (String string : sphereEffect.split("'")[1].split("'")[0].replace("]", "").replace("[", "").split(",")) {
                if (c == 2) c = 0;
                String b = string.replace("{\"lvl\":", "").replace("\"nbtName\":", "").replace("\"", "").replace("}", "").replace("\n", "");
                if (c == 1) {
                    eff.add(b + ":" + lvl);
                    lvl = "";
                    c = 2;
                }
                if (c == 0) {
                    lvl = b;
                    c++;
                }
            }

            if (eff.size() != effect.size()) return false;
            for (String s : effect) {
                boolean flag = true;
                for (String string : eff) {
                    if (s.equalsIgnoreCase(string)) {
                        flag = false;
                        break;
                    }
                }
                if (flag) {
                    return false;
                }
            }
        }

        // Проверка атрибутов
        if (!attributes.isEmpty()) {
            for (EquipmentSlotType slot : EquipmentSlotType.values()) {
                Multimap<Attribute, AttributeModifier> multimap = stack.getAttributeModifiers(slot);
                if (!multimap.isEmpty()) {
                    for (String attribute : attributes) {
                        boolean flag = true;
                        for (Map.Entry<Attribute, AttributeModifier> entry : multimap.entries()) {
                            if (attribute.equals(entry.getKey().getAttributeName() + entry.getValue().getAmount())) {
                                flag = false;
                                break;
                            }
                        }
                        if (flag) {
                            return false;
                        }
                    }
                }
            }
        }

        // Проверка зачарований
        if (!enchantments.isEmpty()) {
            List<String> enchantmentList = getEnchantments(stack);
            if (strictCheck) {
                if (enchantmentList.size() != enchantments.size()) return false;
                for (String check : enchantmentList) {
                    boolean flag = true;
                    for (String in : enchantments) {
                        if ((in.contains("farmer") && in.contains(check)) || check.equalsIgnoreCase(in)) {
                            flag = false;
                            break;
                        }
                    }
                    if (flag) {
                        return false;
                    }
                }
            } else {
                for (String check : enchantments) {
                    boolean flag = true;
                    for (String in : enchantmentList) {
                        if ((check.contains("farmer") && in.contains(check)) || check.equalsIgnoreCase(in)) {
                            flag = false;
                            break;
                        }
                    }
                    if (flag) {
                        return false;
                    }
                }
            }
        }

        // Проверка строк
        if (!line.isEmpty()) {
            for (String check : line) {
                boolean flag = true;
                for (ITextComponent tooltip : stack.getTooltip(mc.player, ITooltipFlag.TooltipFlags.NORMAL)) {
                    if (tooltip.equals(stack.getDisplayName())) continue;
                    if (checkString(check, tooltip.getString())) {
                        flag = false;
                        break;
                    }
                }
                if (flag) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean checkString(String stringFromPlayer, String stringToCheck) {
        return Pattern.compile("\\b" + Pattern.quote(stringFromPlayer) + "\\b", Pattern.CASE_INSENSITIVE).matcher(stringToCheck).find();
    }

    private List<String> getEnchantments(ItemStack stack) {
        List<String> list = new ArrayList<>();
        if (stack.getEnchantmentTagList() == null) return list;

        for (INBT enchantment : stack.getEnchantmentTagList()) {
            String tag = enchantment.toString().replace("{", "").replace("}", "");
            StringBuilder lvl = new StringBuilder();
            for (char c : tag.split(",")[0].toCharArray()) {
                try {
                    lvl.append(Integer.parseInt(String.valueOf(c)));
                } catch (Exception ignored) {
                }
            }
            StringBuilder enchantName = new StringBuilder();
            boolean targ = false;
            for (char c : tag.split(",")[1].toCharArray()) {
                if (c == '\"') {
                    if (!targ) {
                        targ = true;
                        continue;
                    } else {
                        break;
                    }
                }
                if (targ) {
                    enchantName.append(c);
                }
            }
            if (!enchantName.toString().isEmpty()) {
                list.add(enchantName.toString().split(":")[1] + ":" + lvl);
            }
        }
        return list;
    }
}
