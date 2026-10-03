package me.uc_hussein.ultraslogin.common.bridge;

import java.util.List;

/** One slot of a menu rendered by the backend bridge. Text fields are Adventure JSON (Gson), already localized and rendered by the proxy. */
public record GuiItemModel(int slot, String material, String name, List<String> lore, String actionId, boolean glow) {
}
